#!/usr/bin/env bash
#
# Moves Kotlin sources from kotlin-sdk's MCP types to misk.mcp.protocol, for the misk-mcp release that serves MCP
# natively. Usage: migrate-from-kotlin-sdk.sh <source dir>...
#
# It rewrites the mechanical changes in place, then lists what needs a person: kotlin-sdk APIs with no misk
# equivalent, misk-mcp APIs that were removed, and reads of CallToolResult.structuredContent, which is now any JSON
# value rather than an object.
set -euo pipefail

if [ $# -eq 0 ]; then
  echo "usage: $0 <source dir>..." >&2
  exit 1
fi

files=$(grep -rlE --include='*.kt' 'io\.modelcontextprotocol\.kotlin\.sdk' "$@" || true)

for file in $files; do
  perl -0pi -e '
    my $doubleSchema = /^import io\.modelcontextprotocol\.kotlin\.sdk\.types\.DoubleSchema$/m;

    # Method.Defined.ToolsCall.value -> McpMethod.TOOLS_CALL
    s{\bMethod\.Defined\.(\w+)\.value\b}{
      my $name = $1; $name =~ s/(?<=[a-z0-9])(?=[A-Z])/_/g; "McpMethod." . uc($name)
    }ge;
    s{^import io\.modelcontextprotocol\.kotlin\.sdk\.types\.Method$}{import misk.mcp.protocol.McpMethod}m;

    s{\bio\.modelcontextprotocol\.kotlin\.sdk\.types\.}{misk.mcp.protocol.}g;
    # The package as a string, as in KotlinPoet ClassName("io.modelcontextprotocol.kotlin.sdk.types", "TextContent")
    s{"io\.modelcontextprotocol\.kotlin\.sdk\.types"}{"misk.mcp.protocol"}g;

    if ($doubleSchema) {
      s{\bDoubleSchema\b}{NumberSchema}g;
    }

    # Keep each import block sorted, as ktfmt does
    s{((?:^import [^\n]+\n)+)}{
      my %seen; join "", sort grep { !$seen{$_}++ } split /^/m, $1
    }gme;
  ' "$file"
done

echo "Updated $(echo -n "$files" | grep -c '^' || true) files."
echo
echo "Left to migrate by hand (see misk's CHANGELOG):"
# One perl process for every file, so "nothing" prints at most once. Other libraries have their own symbols with these
# names, so removed symbols are flagged only in files that use misk-mcp or kotlin-sdk, and structuredContent only in
# files that use misk.mcp.protocol and not as a named constructor argument.
find "$@" -name '*.kt' -print0 | perl -e '
  my @paths = do { local $/ = "\0"; map { chomp; $_ } <STDIN> };
  my $found = 0;
  for my $path (@paths) {
    open my $fh, "<", $path or die "$path: $!";
    my @lines = <$fh>;
    close $fh;
    my $usesMcp = grep { /\bmisk\.mcp\b|\bio\.modelcontextprotocol\b/ } @lines;
    my $usesProtocol = grep { /\bmisk\.mcp\.protocol\b/ } @lines;
    for my $i (0 .. $#lines) {
      my $line = $lines[$i];
      if ($line =~ /io\.modelcontextprotocol\.kotlin\.sdk\b/
          || ($usesMcp && $line =~ /\b(?:asMcpStreamableHttpClient|currentServerSession|currentClientConnection|createTypedElicitation|McpGet|McpDelete|McpWebSocket|withWebSocket|withResponseChannel|McpSessionHandler|McpSessionHandlerModule|McpSessionId|SESSION_ID_HEADER|ServerSession|buildCallToolRequest)\b|\bMethod\.Defined\b/)
          || ($usesProtocol && $line =~ /\bstructuredContent\b(?!\s*=[^=])/)) {
        print "$path:", $i + 1, ": $line";
        $found = 1;
      }
    }
  }
  print "  nothing\n" unless $found;
'
