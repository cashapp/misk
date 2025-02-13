package misk.wasm

import io.github.charlietap.chasm.embedding.dsl.imports
import io.github.charlietap.chasm.embedding.shapes.Import
import io.github.charlietap.chasm.embedding.shapes.Store

// Stolen From io.github.charlietap.chasm.integration.WehTest
fun wasiImports(store: Store): List<Import> {
  return imports(store) {
      function {
          moduleName = "wasi_snapshot_preview1"
          entityName = "fd_write"
          type {
              params {
                  i32()
                  i32()
                  i32()
                  i32()
              }
              results {
                  i32()
              }
          }
          reference { emptyList() }
      }
      function {
          moduleName = "wasi_snapshot_preview1"
          entityName = "random_get"
          type {
              params {
                  i32()
                  i32()
              }
              results {
                  i32()
              }
          }
          reference { emptyList() }
      }
      function {
          moduleName = "wasi_snapshot_preview1"
          entityName = "clock_time_get"
          type {
              params {
                  i32()
                  i64()
                  i32()
              }
              results {
                  i32()
              }
          }
          reference { emptyList() }
      }
      function {
          moduleName = "wasi_snapshot_preview1"
          entityName = "poll_oneoff"
          type {
              params {
                  i32()
                  i32()
                  i32()
                  i32()
              }
              results {
                  i32()
              }
          }
          reference { emptyList() }
      }
      function {
          moduleName = "wasi_snapshot_preview1"
          entityName = "clock_time_get"
          type {
              params {
                  i32()
                  i64()
                  i32()
              }
              results {
                  i32()
              }
          }
          reference { emptyList() }
      }
      function {
          moduleName = "wasi_snapshot_preview1"
          entityName = "proc_exit"
          type {
              params {
                  i32()
              }
              results { }
          }
          reference { emptyList() }
      }
      function {
          moduleName = "wasi_snapshot_preview1"
          entityName = "fd_write"
          type {
              params {
                  i32()
                  i32()
                  i32()
                  i32()
              }
              results {
                  i32()
              }
          }
          reference { emptyList() }
      }
      function {
          moduleName = "wasi_snapshot_preview1"
          entityName = "clock_time_get"
          type {
              params {
                  i32()
                  i64()
                  i32()
              }
              results {
                  i32()
              }
          }
          reference { emptyList() }
      }
      function {
          moduleName = "wasi_snapshot_preview1"
          entityName = "args_sizes_get"
          type {
              params {
                  i32()
                  i32()
              }
              results {
                  i32()
              }
          }
          reference { emptyList() }
      }
      function {
          moduleName = "wasi_snapshot_preview1"
          entityName = "args_get"
          type {
              params {
                  i32()
                  i32()
              }
              results {
                  i32()
              }
          }
          reference { emptyList() }
      }
      function {
          moduleName = "wasi_snapshot_preview1"
          entityName = "fd_prestat_get"
          type {
              params {
                  i32()
                  i32()
              }
              results {
                  i32()
              }
          }
          reference { emptyList() }
      }
      function {
          moduleName = "wasi_snapshot_preview1"
          entityName = "fd_prestat_dir_name"
          type {
              params {
                  i32()
                  i32()
                  i32()
              }
              results {
                  i32()
              }
          }
          reference { emptyList() }
      }
      function {
          moduleName = "wasi_snapshot_preview1"
          entityName = "path_open"
          type {
              params {
                  i32()
                  i32()
                  i32()
                  i32()
                  i32()
                  i64()
                  i64()
                  i32()
                  i32()
              }
              results {
                  i32()
              }
          }
          reference { emptyList() }
      }
      function {
          moduleName = "wasi_snapshot_preview1"
          entityName = "fd_filestat_get"
          type {
              params {
                  i32()
                  i32()
              }
              results {
                  i32()
              }
          }
          reference { emptyList() }
      }
      function {
          moduleName = "wasi_snapshot_preview1"
          entityName = "fd_seek"
          type {
              params {
                  i32()
                  i64()
                  i32()
                  i32()
              }
              results {
                  i32()
              }
          }
          reference { emptyList() }
      }
      function {
          moduleName = "wasi_snapshot_preview1"
          entityName = "fd_pread"
          type {
              params {
                  i32()
                  i32()
                  i32()
                  i64()
                  i32()
              }
              results {
                  i32()
              }
          }
          reference { emptyList() }
      }
      function {
          moduleName = "wasi_snapshot_preview1"
          entityName = "fd_close"
          type {
              params {
                  i32()
              }
              results {
                  i32()
              }
          }
          reference { emptyList() }
      }
  }
}
