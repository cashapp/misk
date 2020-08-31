# Misk encryption packet format
## Overview
Misk uses Tink to encrypt data, which uses Additional Authentication Data (AAD) 
to authenticate ciphertext.

Instead of using Tink's byte array AAD, 
Misk introduces a new, higher level abstraction, that’ll be used instead of the encryption 
interfaces Tink exposes to users.

The main reasons to do this are:
1. Preventing the misuse of AAD
2. Preventing undecipherable ciphertext from being created
3. Exposing a user friendlier interface
## Details
The use of AAD can be confusing to users. 
In Tink, AAD is represented by an array of bytes, which is hard to deal with when debugging
and can be easily corrupted and misused.

On top of that, different languages and environments may parse byte arrays in different ways.

Instead of letting misk-crypto users supply their own byte array as AAD, 
we decided to expose a more convenient data structure - a map of strings where 
each key-value pair in the map represents a context variable and its value.

When encrypting/decrypting with misk-crypto
the encryption context map will be serialized to a byte array and used instead.
## Encryption Context Specification
- `Map<String, String>`
- `null` values are *not* allowed
- Empty maps are *not* allowed
- Empty keys or values are *not* allowed
- Map keys and value lengths are limited to `Short.MAX_VALUE`
- The entire serialized encryption context is also limited to `Short.MAX_VALUE`
- EC is optional, and can be completely omitted from the encryption operation

The encryption context will be serialized using the following format:
```
[ [ varint: pair count ] 
  [ pairs: 
    (
      [ varint: key length ] [ bytearray: key ]
      [ varint: value length ] [ bytearray: value ]
    )*
] ]
```
All lengths are encoded is `varint` using [Google's Protocol Buffers](https://developers.google.com/protocol-buffers/docs/encoding#varints) encoding
## Misk Ciphertext Format
The serialized EC used when creating the ciphertext and the ciphertext are put together in the following format.
```
[ 0xEE: magic byte + version ]
[ varint: AAD length ] 
[ AAD ]
[ tink ciphertext ]
```
| Size | Description | Type |
|------|-------------|------|
| 1 | Schema version, encoded as a single byte. Currently set to 0xEE | Byte |
| varint | Serialized AAD length | Integer |
| AAD array length | Serialized AAD | ByteArray |
| Ciphertext length | Ciphertext | ByteArray |

__Note__: If the ciphertext doesn't have an encryption context associated with it, the AAD length field is set to 0.
