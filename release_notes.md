# Version: SNAPSHOT

Under development in `master` branch.


# Version: 3.2.0

### New Features
- when running JAR file with JDK 17+, no longer need to manually specify --add-opens commands
- schema validation for OpenAPI schemas, with summary of issues printed in the console logs
- generated tests that detect faults have now comments highlighting those faults. 

### Bug Fixes
- fixed release of JAR requiring JDK 21 by mistake
- fixed issue in Python and JavaScript output for when JSON responses are wrongly free text (e.g., unquoted strings)

### Addressed GitHub Issues

- #822: additionalProperties: [true/false] causes crash
- #989: TestCaseWriter login cookie variable name
- #1055: Unable to start EVoMaster in public petstore API
- #1069: Error in EvoMaster 3.1.0: Black-Box Testing Initialization Failure with InvocationTargetException and NoSuchMethodError

---
# Version: 3.1.0

### New Features
- new fully-supported output formats: Python and JavaScript 
- support for JDK 21
- body payloads for GET, HEAD and DELETE are not valid in OpenAPI. However, they can be valid in some special cases in HTTP (as of RFC 9110). Now, EvoMaster can test those cases as well, but only for DELETE (for GET it is not possible, as current version of HTTP library in EvoMaster is faulty, i.e., it cannot handle such case).
- update of internal parameter settings to improve performance, based on large tuning experiments. 
- --prematureStop option can now be used to stop search prematurely if no improvement has been obtained in the last specified amount of time 

### Changes
- output format for black-box testing now defaults to Python instead of throwing an error
- --outputFolder now defaults to a more meaningful value, i.e., "generated_tests" 

### Bug Fixes
- better dealing with reset of SQL tables where names in queries used quotes, and/or table is not accessible in database.
- fixed issue related to SQL insertion failures preventing all SQL insertions from being part of the generated tests, leading to flaky tests.
- fixed handling of JsonProperty

### Addressed GitHub Issues
- #834: Authentication required when accessing schema
- #957: Generated test cases fail due to 'checkInstrumentation' 
- #962: Fail to parse endpoint due to body.content must not be null when requestBody has $ref
- #988: Is it normal that EvoMaster combined "sucess" and "other" tests into "fault_representatives"?

---
# Version: 3.0.0

### Breaking Changes
- Authentication credential definitions have been overhauled. This breaks current white-box drivers. For example, `AuthenticationDto` is now moved into a `auth` sub-package, i.e., `org.evomaster.client.java.controller.api.dto.auth`. Cookie and token based authentication definitions are now replaced with `LoginEndpointDto`. See new [auth documentation](docs/auth.md). 
- SQL related configuration DTOs have been moved into new `org.evomaster.client.java.sql` package from old naming `org.evomaster.client.java.controller.db`. 

### New Features
- Handling configuration files to specify _options parameters_ and _authentication credentials_ (especially needed for black-box testing). An empty `em.yaml` file is now created automatically if missing, with documentation as comments. Location can be changed with `--configPath` option. 
- Possibility to filter endpoints to fuzz based on their OpenAPI tags (using `--endpointTagFilter`).
- Bypassing HTTPS checks. Now it is possible to fuzz APIs using HTTPS where their certificates are expired or invalid. 

### White-Box Improvements
- Handling of `Base64.getDecoder()`
- Handling of `@Email` javax constraints

### Bug Fixes

- fixed wrong number of covered endpoints in console logs
- fixed issues in reading OpenAPI schemas from file-system on Windows
- fixed issues related to endpoint definitions ending with a `/`, e.g., `/foo` and `/foo/` are technically 2 different endpoints.

### Addressed GitHub Issues
- #701: EvoMaster process terminated abruptly when using BlackBox and a local file for schema
- #759: Cannot find EvoMaster_Test.java file in the output folder
- #820: OpenApi spec default value is ignored
- #862: Errors while saving tests to src/em
- #873: Bug in GraphQL Blackbox mode
- #906: WTSAlgorithm Crossover Bug
- #914: duplicate of #701

---
# Version 2.0.0 and Earlier

Unfortunately, we only started in 2024 to write release notes.
There is no release notes for older versions of _EvoMaster_.

