# Contributing

If you would like to contribute code to this project you can do so through GitHub by
forking the repository and sending a pull request.

When submitting code, please make every effort to follow existing conventions
and style in order to keep the code as readable as possible.

Before your code can be accepted into the project you must also sign the
[Individual Contributor License Agreement (CLA)][1].

## Breaking changes

We use the [Kotlin binary compatibility validator][2] to check for API changes. If 
a change contains an API change and breaks the build, run the `:apiDump` task and 
commit the resulting changes to the `.api` files. `.api` files should not have 
removals and additions in the same change.

 [1]: https://spreadsheets.google.com/spreadsheet/viewform?formkey=dDViT2xzUHAwRkI3X3k5Z0lQM091OGc6MQ&ndplr=1
 [2]: https://github.com/Kotlin/binary-compatibility-validator
