# Product Vision

## Overview

This is a demonstration repository that shows how Clean Architecture keeps business logic portable across serverless cloud platforms. It serves as a reference implementation and live-coding companion for conference talks, workshops, and educational content.

The project implements a simple "Docs Flow" use case (document upload → analysis → email notification) deployed to both AWS Lambda and Azure Functions from the same business logic codebase.

## Purpose

- **Conference demo repo** — Used for live-coding during talks to demonstrate how clean architecture enables cloud portability
- **Reference implementation** — A working example people can clone, study, and adapt for their own projects
- **Teaching tool** — Shows the practical benefits of separating domain logic from infrastructure concerns

## Target Audience

- Developers attending conference talks on clean architecture and serverless
- Engineers evaluating clean architecture patterns for multi-cloud deployments
- Teams looking for a working example of Kotlin + Spring Cloud Function on AWS and Azure

## Demo Strategy

The repository maintains an **init branch** (e.g., `feature/kotlin-conf-demo-init`) that provides a starting point for live-coding demos. During talks, the presenter can:

1. Start from the init branch with placeholder values and partial implementations
2. Live-code key pieces to demonstrate how clean architecture layers work
3. Show how the same business logic deploys to both AWS and Azure
4. Demonstrate the deployment pipeline and health checks

## Key Principles

- **Simplicity over completeness** — The codebase should be easy to understand in a conference setting, not production-hardened
- **Clear layer separation** — Each architectural layer should be obviously distinct and easy to explain
- **Dual-cloud deployment** — Both AWS and Azure deployments must work to demonstrate portability
- **Live-demo friendly** — The project should build quickly, deploy reliably, and have clear health check verification

## Non-Goals

- This is NOT a production application — it's a teaching tool
- No need for enterprise features (auth, multi-tenancy, complex error handling)
- No need for high availability or disaster recovery
- Performance optimization is for demo purposes (showing SnapStart/priming), not production SLAs

## Success Criteria

- Audience can understand the architecture within 5 minutes of explanation
- Live-coding demo works reliably from the init branch
- Both AWS and Azure deployments succeed and pass health checks
- The repository README provides enough context for self-guided learning
