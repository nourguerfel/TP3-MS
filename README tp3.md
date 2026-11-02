# TP3-MS
# TP3 - Calculator Microservices with Spring Boot

Name:Guerfel Nour 
Course: Architecture MicroServices - MPSRCC1

## Projects

- `calcul-service` (port 8081): does the 4 calculations, exposes `/calcul`.
- `historique-service` (port 8082): stores operations in memory, exposes `/operations`. It calls `calcul-service` by HTTP to get each result.

## How to run

Start `calcul-service` first, then `historique-service`. In each project folder:

`mvn clean package`
`java -jar target/calcul-service-0.0.1-SNAPSHOT.jar`
`java -jar target/historique-service-0.0.1-SNAPSHOT.jar`

Or run each `...Application` class from IntelliJ.

Note: `historique-service` has the property `spring.jackson.deserialization.fail-on-null-for-primitives=false` in `application.properties`. Without it, Spring Boot 4 (Jackson 3) rejects a JSON body without `id` and `resultat` (400 error).

---

## Part A: From web service to microservice

**A.4 Design questions**

1. Advantage: each service is independent, so it can be changed, started or deployed alone. Disadvantage: an HTTP call is slower than a method call, and it can fail if the network or the other service is down.
2. If `calcul-service` is stopped, these still work: GET `/operations`, GET `/operations/{id}`, DELETE `/operations/{id}`, and PUT with an unknown id (404). These fail: all `/calcul/...` requests, POST `/operations`, and PUT on an existing id (they need a calculation).
3. A port can be used by only one program at a time. The second one would fail with "Port already in use".
4. 503 (Service Unavailable).

## Part B: Project creation

No questions. Both projects were generated with Spring Initializr (Maven, Java 17, Spring Boot 4.0.9, dependency Spring Web, packaging Jar).

## Part C: First microservice

**C.2 Correspondence Spring / JAX-RS**

| Spring (TP3) | JAX-RS (TP2) | Role |
|---|---|---|
| `@RestController` | no direct equivalent | Marks the class as a REST controller, return values are sent to the client |
| `@RequestMapping("/calcul")` | `@Path("/calcul")` on the class | Base path of the resource |
| `@GetMapping("/somme")` | `@GET` + `@Path("/somme")` | Answers GET requests on /somme |
| `produces = MediaType.TEXT_PLAIN_VALUE` | `@Produces(MediaType.TEXT_PLAIN)` | The response is plain text |
| `@RequestParam("a") int a` | `@QueryParam("a") int a` | Reads parameter a from the URL |
| `ResponseEntity.ok(...)` | `Response.ok(...).build()` | Builds a response with status 200 |

**C.6 Parts of the URL** `http://localhost:8081/calcul/somme?a=2&b=3`

| Part | Origin |
|---|---|
| `localhost:8081` | Local machine and port set by `server.port` in `application.properties` |
| `/calcul` | `@RequestMapping("/calcul")` on the class |
| `/somme` | `@GetMapping("/somme")` on the method |
| `?a=2&b=3` | Query parameters read by `@RequestParam("a")` and `@RequestParam("b")` |

**Question C.1:** `RestWebApp` and `/api` disappeared. `RestWebApp` was the name of the `.war` deployed in Tomcat, and `/api` came from `@ApplicationPath`. Now the application runs alone with its own embedded Tomcat, so there is no application name and no prefix. The port is also 8081 instead of 8080.

**Question C.2:** POST on a GET-only URL gives 405 (Method Not Allowed). It is the same code as in TP2.

## Part D: Complete calculator

**Question D.1:** Spring rejects the request with 400 (Bad Request) when a parameter is missing or is not an integer, and the method is not executed. JAX-RS used 0 and calculated anyway. Spring's behavior is more RESTful: a bad request should be rejected, not give a false result.

**Question D.2:** The JSON error contains the fields `timestamp`, `status`, `error` and `path`.

**Question D.3:**

| Spring | JAX-RS |
|---|---|
| `@RequestParam` | `@QueryParam` |
| `@PathVariable` | `@PathParam` |

To read the id in `/operations/{id}` we use `@PathVariable`, because the id is in the path of the URI.

## Part E: JSON

**Question E.1:** Yes, they are equivalent. In JSON an object is a set of key/value pairs, and the order of the fields does not matter. This time the conversion was done by Jackson (JSON-B in TP2).

**Question E.2:** Spring can only produce formats for which it has a converter. It has Jackson for JSON, but no XML converter is present in the project. So `Accept: application/xml` gives 406 (Not Acceptable), even if the method does not mention any format.

**Exercise E.4:** `divisionDetail` returns `ResponseEntity<Resultat>`: status 400 if `b == 0`, otherwise 200 with the `Resultat` object converted to JSON.

## Part F: historique-service

**F.6 and F.7 results**

| N° | Request | Status | Response |
|---|---|---|---|
| 1 | POST somme 8, 2 | 201 | `{"id":1,"operateur":"somme","a":8,"b":2,"resultat":10.0}` |
| 2 | POST division 7, 2 | 201 | id 2, resultat 3.5 |
| 3 | GET `/operations` | 200 | list of operations |
| 4 | GET `/operations/2` | 200 | operation 2 |
| 5 | GET `/operations/99` | 404 | no body |
| 6 | POST division 7, 0 | 400 | `{"erreur":"Calcul impossible (division par 0 ?)"}` |
| 7 | POST operator racine | 400 | `{"erreur":"Opérateur inconnu"}` |
| 8 | GET `/operations` (calcul-service stopped) | 200 | the list |
| 9 | GET `/operations/1` (calcul-service stopped) | 200 | operation 1 |
| 10 | POST produit 3, 4 (calcul-service stopped) | 503 | `{"erreur":"calcul-service est indisponible"}` |

After restarting `calcul-service`, request 10 gives 201 with id 3.

**Question F.1:** Reads still work because they do not call `calcul-service`: `historique-service` only reads the operations stored in its own memory. In TP2 this was not possible, because everything was in the same `.war`: if the application stopped, everything stopped.

**Question F.2:** Without the `catch (ResourceAccessException e)` block, the exception would not be handled and would go up to Spring. The client would get a 500 (Internal Server Error) instead of a clear 503. The 503 is more correct, because it is not a server bug, it is a service we depend on that is unavailable.

**F.8 PUT**

| Situation | Status | Body |
|---|---|---|
| No operation with this id | 404 | `{"erreur":"Opération introuvable"}` |
| Calculation impossible (unknown operator, division by 0) | 400 | `{"erreur":"..."}` |
| calcul-service unreachable | 503 | `{"erreur":"calcul-service est indisponible"}` |
| Successful update | 200 | the updated operation |

Test: PUT `/operations/1` with `{"operateur":"produit","a":8,"b":2}` gives 200 and resultat 16.0.

**F.9 DELETE**

| Situation | Status | Body |
|---|---|---|
| No operation with this id | 404 | `{"erreur":"Opération introuvable"}` |
| Successful deletion | 204 | none |

Test order: DELETE gives 204, then GET gives 404, then DELETE gives 404.

---

## Summary questions

**1. Where each difference of table A.3 was observed**

| Aspect | Step |
|---|---|
| Technology (Spring Boot instead of JAX-RS) | B.3, B.4 |
| Split into two applications | A.2, F.1 |
| Build result (.jar instead of .war) | B.3, C.5 |
| Server (embedded Tomcat) | C.5 |
| Port (one per microservice) | C.4, F.1 |
| Address (no app name, no /api) | C.6, C.7 |
| Activation (`@SpringBootApplication`) | B.4 |
| REST resource (`@RestController`) | C.2 |
| HTTP verbs | C.2, F.5, F.8, F.9 |
| Parameters | C.2, D.4, F.5 |
| HTTP response (`ResponseEntity`) | C.2, D.2 |
| JSON conversion (Jackson) | E.2 |
| Object creation (`@Service`, injection) | C.1, C.2, F.4 |
| Communication (HTTP between services) | F.3, F.7 |

**2. Correspondence JAX-RS / Spring**

| JAX-RS (TP2) | Spring (TP3) |
|---|---|
| `@ApplicationPath` | `@SpringBootApplication` |
| `@Path` | `@RequestMapping` |
| `@GET`, `@POST`, `@PUT`, `@DELETE` | `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` |
| `@QueryParam` | `@RequestParam` |
| `@PathParam` | `@PathVariable` |
| `@Consumes` (JSON body) | `@RequestBody` |
| `@Produces` | `produces = ...` |
| `Response` | `ResponseEntity` |

**3. Build and run without IntelliJ.** In each project folder (use two terminals):
`mvn clean package` (or `mvnw clean package`)
then `java -jar target/calcul-service-0.0.1-SNAPSHOT.jar` in the first one, and `java -jar target/historique-service-0.0.1-SNAPSHOT.jar` in the second one. No Tomcat to install, the server is inside the `.jar`.

**4.** A microservice is a web service, because it communicates by HTTP (our two services do). But a web service is not always a microservice: the `RestWebApp.war` of TP2 was a web service, but it was one big application deployed in an external Tomcat, not a small independent application with its own server.

**5.** The address of `calcul-service` is fixed in the configuration (port 8081). With three copies, all requests would still go to the same one: no load is shared, and if that copy stops, `historique-service` fails. We need a mechanism that finds the copies and spreads the requests (TP4).

**6.** Stateless means the server does not keep the state of the client (session) between two requests. The list of operations is not the state of a client: it is data of the resource, the same for everybody. Each request contains everything the server needs to answer it.

**7.** Two identical POST requests create two different operations (two ids), so the effect changes with each repetition: POST is not idempotent. Two identical PUT requests leave the same final state as one (operation 1 is replaced with the same data): PUT is idempotent. The answer is the same as in TP2, because it comes from the HTTP rules and not from the technology used.
