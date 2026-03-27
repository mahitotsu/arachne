# Secure Downstream Tools Sample

This sample shows a backend-oriented custom tool pattern where Arachne tools use Spring Security state at execution time without exposing raw security internals to the model.

It combines two concerns that usually appear together in real backend tools:

- executor-boundary propagation of caller security context
- downstream API calls that need caller-derived authorization

The sample is deterministic and Bedrock-free so you can inspect the wiring locally.

## What This Sample Teaches

- use `ExecutionContextPropagation` to carry the caller `SecurityContext` into parallel tool execution
- read current caller data through `SecurityContextHolder` inside the tool or service layer
- expose only an allowlisted authorization view to the model
- call a downstream service with `RestClient` using caller-derived authorization without taking tokens from model input

## Security Boundary

This sample intentionally does **not** expose the raw `SecurityContext`, access token, or complete claim set to the model.

The model can ask for:

- the current operator capability view
- a customer profile summary fetched through a downstream client

The model never receives:

- bearer tokens
- raw `SecurityContext` objects
- authentication implementation details beyond the allowlisted capability DTO

## Prerequisites

- Java 21
- Maven

The sample depends on the local snapshot of this repository, so install the root project first:

```bash
mvn install
```

## Run The Demo

```bash
cd samples/secure-downstream-tools
mvn spring-boot:run
```

Expected output shape:

```text
Arachne secure downstream tools sample
request> Check what the current operator can do and fetch customer cust-42.
final.reply> capability view and downstream profile fetched
capabilities.view> CurrentOperatorCapabilities[...]
profile.summary> CustomerProfileSummary[...]
downstream.requests> [cust-42]
downstream.authHeaders> [Bearer token-demo-42]
```

## Design Notes

- `CurrentOperatorCapabilitiesTool` reads the current authentication at execution time and maps it into a safe DTO.
- `CustomerProfileTool` also reads the current authentication, but it uses the caller-derived token only for the downstream `RestClient` call.
- `SecurityContextPropagationConfiguration` restores the current security context around parallel tool execution.
- the downstream service is a local deterministic stub so the sample stays runnable without external infrastructure.