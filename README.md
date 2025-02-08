# API-Flow Documentation

## Why API-Flow?

Microservice architecture operates on the **request/response** principle, where each request is processed to provide the user with a clear response – either successful or failed. This process works similarly to a web transaction, aiming to complete within a reasonable timeframe, usually within 45 seconds. A well-designed microservice should be **stateless**, meaning it does not retain state directly within the container. Instead, the state is managed via an external database, Redis, or another stateful storage solution.

The stateless nature of microservices allows for simplicity and scalability. Although the microservice itself is stateless, the application state must be managed in external storage to ensure proper system functionality.

---

## Different Use Cases

### Cron Job

An internal cron job is a stateful operation, which conflicts with the stateless nature of microservices. When multiple containers are running simultaneously, it’s necessary to manage parallel access with a **global lock**. If no container is running, the cron job may fail without any record that the task was not executed. Error reporting must also be considered, meaning the system needs a method to log where and how errors occurred.

An **external cron job** addresses these issues by being implemented as a web service, which can be called by an external cron daemon. This approach provides several advantages:
  - **Logging**: Every call, whether successful or unsuccessful, is logged.
  - **Soft Semaphore**: If multiple instances are deployed, only one handles the job.
  - **Strict Single Execution**: If the task must run strictly only once, a global lock must be implemented within the microservice itself.

### Asynchronous Communication: A Web Service Calling Additional Services

In asynchronous communication, a web service may call additional services running in parallel, potentially extending execution time. This process is no longer stateless because it must maintain the state of individual steps. If the container restarts, the process cannot continue from where it left off. Furthermore, if the service calls other services with limited concurrency or requires parallel calls with subsequent response joining, it increases the complexity of the process.

### Broker with Queue/Topic

An architecture with a broker using a **queue** or **topic** introduces additional complexity. This approach uses a unique communication protocol and external libraries, which come with a different authorization method and distinct methods for materializing and dematerializing data compared to traditional web services. For proper system functionality, logging, activity tracing, and performance monitoring (metering) must be handled independently.

---

## What is API-Flow?

API-Flow is an application designed to manage background processes in a microservices architecture. Its primary role is to **materialize the state of individual flows**; each running instance of a flow is called a **case**. This case has input parameters and a defined **workflow** – a process dictating the tasks to be executed sequentially. Each case contains a list of **steps**, which are executed one after another.

Steps can be of different types:
  - **Single Step**: A single task executed once.
  - **Multi Step**: An iterator that returns a list of items, triggering the step for each item in the list.

Each step contains a list of **workers**, where each worker represents an HTTP call definition for a web service (including method, link, headers, and body). A running instance of a worker is called a **task**.

All workers within a step run **in parallel**. A worker can be configured with a `where` condition to determine if a specific task should be executed. Each case generates a **trace map** of all calls, allowing for monitoring of process flow and alerting on failed calls.

---

### Types of Worker Calls

API-Flow supports multiple types of worker calls:

1. **Synchronous Web Service Call**: This is a standard call where a request is sent, and a response is awaited.
2. **Asynchronous Call with Callback**: In this call type, the first request includes a callback link in the header. When the web service finishes its work, it sends a POST with the result to the callback link.
3. **Asynchronous Call via Queue**: In this type, the request is stored in a queue, from which a sidecar process retrieves it and calls the target web service. The sidecar then sends the result back to the flow via a callback.

API-Flow thus offers a robust approach to managing asynchronous processes, enabling parallel execution, and monitoring the state of individual tasks within a microservices architecture.

# Flow Configuration Documentation

This documentation describes the data objects used in API-Flow, including their attributes and purposes.

## 1. `FlowDef`

The `FlowDef` class defines the main flow object, which contains basic information about the flow and its steps.

### Attributes

- **`code`**: 
  - Type: `String`
  - Description: Unique identifier for the flow.

- **`auth`**: 
  - Type: `String`
  - Description: Authentication information used when executing the flow.

- **`steps`**: 
  - Type: `List<FlowStepDef>`
  - Description: A list of steps (`FlowStepDef`) that define the individual parts of the flow.

- **`labels`**: 
  - Type: `Map<String, String>`
  - Description: A map with additional information or metadata about the flow.

- **`response`**: 
  - Type: `Map<String, Object>`
  - Description: A map that contains expressions for obtaining responses from the workers.

---

## 2. `FlowStepDef`

The `FlowStepDef` class defines the individual steps within the flow. Each step may contain workers that perform HTTP requests.

### Attributes

- **`code`**: 
  - Type: `String`
  - Description: Unique identifier for the step.

- **`itemsExpr`**: 
  - Type: `String`
  - Description: An expression that generates a list of items for this step (e.g., an iterator).

- **`workers`**: 
  - Type: `List<FlowWorkerDef>`
  - Description: A list of workers (`FlowWorkerDef`) that are part of this step.

---

## 3. `FlowWorkerDef`

The `FlowWorkerDef` class defines a worker that executes a specific HTTP request within the flow step.

### Attributes

- **`code`**: 
  - Type: `String`
  - Description: Unique identifier for the worker.

- **`path`**: 
  - Type: `String`
  - Description: The path to which the worker attempts to make an HTTP request.

- **`pathExpr`**: 
  - Type: `String`
  - Description: An expression that defines a dynamic path for the HTTP request.

- **`method`**: 
  - Type: `String`
  - Description: The type of HTTP method (e.g., `GET`, `POST`, etc.) used in the request.

- **`headers`**: 
  - Type: `Map<String, String>`
  - Description: A map of HTTP request headers.

- **`params`**: 
  - Type: `Map<String, Object>`
  - Description: A map of parameters sent with the request.

- **`where`**: 
  - Type: `String`
  - Description: A condition that determines whether the worker should be executed.

- **`labels`**: 
  - Type: `Map<String, String>`
  - Description: A map with additional information or metadata about the worker. Labels are written to tracing.

- **`blocked`**: 
  - Type: `boolean`
  - Description: Indicates whether the worker is synchronous or asynchronous.

- **`timeout`**: 
  - Type: `Integer`
  - Description: The time limit (in seconds) for executing the request.

---

## Example YAML Configuration of Flow

Here is an example of flow configuration in YAML format:

```yaml
code: flow
steps:
  - code: step1
    workers:
      - code: worker1
        path: POST
        headers:
          header1: header1
        params:
          a: 1 # constant parameter
          $a: case.params.a # expression
  - code: step2
    itemsExpr: case.assets
    workers:
      - code: worker2
        path: POST
        headers:
          header1: header1
          $header2: case.created # expression
        params:
          a: 1 # constant parameter
          $a: case.params.a # expression
```

This documentation provides an overview of the data objects and their attributes that form the basis for processing flows in the API-Flow application.

---

## Using Expressions

OGNL expressions are used for data manipulation. Expressions can be applied in workers on the `$path`, `where`, `headers`, and `params` items.

For `headers` and `params`, these are maps. If the name of the parameter starts with the `$` symbol, its value is treated as an expression. Otherwise, it is a static property.

If there is only one property in the map named `$`, the output is not a map but rather the value evaluated from the expression parameter value.


