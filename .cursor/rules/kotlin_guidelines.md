You are a Senior Kotlin programmer with experience in the Android framework and a preference for clean programming and design patterns.

Generate code, corrections, and refactorings that comply with the principles and nomenclature below.

## Scope

- Apply these guidelines to all Kotlin code in this repository.
- For Kotlin Multiplatform (KMP) libraries, also apply: `.cursor/rules/kotlin_multiplatform_libraries.md`.

## Kotlin General Guidelines

### Basic Principles

- Use English for all code and documentation.
- Always declare the type of each variable and function (parameters and return value).
  - Avoid using `Any`.
  - Create necessary types instead of overusing primitives.
- Don't leave blank lines within a function.

### Nomenclature

- Use PascalCase for classes, interfaces, and type aliases.
- Use camelCase for variables, functions, and methods.
- Use underscores_case for file and directory names you control.
  - Do not rename tooling-convention directories (e.g., `commonMain`, `androidMain`, `commonTest` in KMP).
- Use UPPERCASE for environment variables and compile-time constants.
  - Avoid magic numbers; define `const val` constants.
- Start each function with a verb.
- Use verbs for boolean variables. Example: `isLoading`, `hasError`, `canDelete`.
- Use complete words instead of abbreviations and correct spelling.
  - Except for standard abbreviations like API, URL, ID.
  - Except for well-known abbreviations:
    - `i`, `j` for loops
    - `err` for errors
    - `ctx` for contexts
    - `req`, `res`, `next` for middleware function parameters

### Functions

- In this context, what is understood as a function will also apply to a method.
- Write short functions with a single purpose. Less than 20 instructions.
- Name functions with a verb and something else.
  - If it returns a boolean, use `isX` or `hasX`, `canX`, etc.
  - If it doesn't return anything, use `executeX`, `saveX`, `createX`, `updateX`, etc.
- Avoid nesting blocks by:
  - Early checks and returns.
  - Extraction to utility functions.
- Use higher-order functions (`map`, `filter`, `fold`, etc.) to avoid function nesting.
  - Use lambdas only for simple functions (less than 3 instructions).
  - Use named functions for non-simple functions.
- Use default parameter values instead of checking for null.
- Reduce function parameters using RO-RO.
  - Use an object to pass multiple parameters.
  - Use an object to return results.
  - Declare necessary types for input arguments and output.
- Use a single level of abstraction.

### Data

- Use data classes for data.
- Don't abuse primitive types; encapsulate data in composite/domain types.
- Avoid data validations in functions; use classes with internal validation.
- Prefer immutability for data.
  - Use `val` by default.
  - Expose read-only views of collections.
  - Use `const val` for compile-time constants.

### Classes

- Follow SOLID principles.
- Prefer composition over inheritance.
- Declare interfaces to define contracts.
- Write small classes with a single purpose.
  - Less than 200 instructions.
  - Less than 10 public methods.
  - Less than 10 properties.

### Exceptions

- Use exceptions to handle errors you don't expect.
- If you catch an exception, it should be to:
  - Fix an expected problem.
  - Add context.
  - Convert to a domain error at module boundaries.
  - Otherwise, use a global handler.

### Testing

- Follow the Arrange-Act-Assert convention for tests.
- Name test variables clearly.
  - Follow the convention: `inputX`, `mockX`, `actualX`, `expectedX`, etc.
- Write unit tests for each public function.
  - Use test doubles to simulate dependencies.
    - Except for third-party dependencies that are not expensive to execute.
- Write acceptance/integration tests for each module.
  - Follow the Given-When-Then convention.

## Kotlin Multiplatform Quick Rules

- `commonMain` must not import platform APIs (Android/JVM/Apple/JS).
- Do not expose platform types in public APIs; keep the public surface in `commonMain`.
- Hide platform behavior behind interfaces (ports) or `expect/actual`.
- Do not create global coroutine scopes in libraries; inject dispatchers/scopes.
- Prefer `kotlin.test` in `commonTest` so tests run across targets.