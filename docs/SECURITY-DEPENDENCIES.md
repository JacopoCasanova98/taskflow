# MS6.7 dependency finding decisions

Review date: 2026-09-16. This is a human decision register, not a committed scanner report.
Scanner severity below preserves the reported rating, even where vendor ratings differ.
All dependency occurrences (including the two Swagger bundles and two Spring Security artifacts) were reviewed.

Spring Boot 3.5.16 remains the latest Maven Central 3.5 patch checked on the review date.
Spring Framework 6.2.20 and Security 6.5.12 fixes are enterprise-only; public Maven Central ends at 6.2.19 and 6.5.11.
No framework major upgrade is made. The Spring rows are FALSE POSITIVE **for current application applicability**,
not a claim that the libraries are patched. Each missing precondition was checked against production source,
configuration and the dependency tree. Explicit resolved-jar filename sets + CVE exclusions expire 2026-12-16, fail if unused,
and must be re-reviewed before introducing a listed feature. Owner: TaskFlow maintainer, MS7/MS9 and dependency maintenance.

Compatible fixes use Boot version properties so each library family stays aligned:

| Dependency | Before → after | Reason |
| --- | --- | --- |
| Tomcat | 10.1.55 → 10.1.60 | Vendor security fixes on the same 10.1 line; no container-major change. |
| PostgreSQL JDBC | 42.7.11 → 42.7.13 | Channel-binding downgrade fix (42.7.12+). |
| Jackson BOM | 2.21.4 → 2.21.6 | Case-insensitive ignored-property binding fix (2.21.5+); aligned BOM. |
| Commons Lang | 3.17.0 → 3.18.0 | ClassUtils recursion fix. |
| Log4j BOM | 2.24.3 → 2.25.5 | MapMessage JSON fix; also clears unrelated Core/bridge CPE matches. TaskFlow uses SLF4J/Logback. |
| Swagger UI WebJar | 5.32.2 → 5.32.15 | Bundled DOMPurify 3.4.13; overrides only the WebJar, preserving springdoc 2.8.17. |

Remove each override when the Boot/springdoc managed version includes its fixes.
The public Swagger asset path is configured to match the overridden WebJar when necessary.

| Initial artifact | Advisory | Scanner severity | Decision and evidence |
| --- | --- | --- | --- |
| commons-lang3-3.17.0.jar | [CVE-2025-48924](https://lists.apache.org/thread/bgv0lpswokgol11tloxnjfzdl7yrc1g1) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
| jackson-databind-2.21.4.jar | [CVE-2026-54515](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-5jmj-h7xm-6q6v) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
| log4j-api-2.24.3.jar | [CVE-2026-34479](https://logging.apache.org/security.html#CVE-2026-34479) | MEDIUM | FALSE POSITIVE — affects Log4j Core / Log4j 1 bridge, neither shipped; removed by the aligned API BOM update, no suppression. |
| log4j-api-2.24.3.jar | [CVE-2026-34477](https://logging.apache.org/security.html#CVE-2026-34477) | MEDIUM | FALSE POSITIVE — affects Log4j Core / Log4j 1 bridge, neither shipped; removed by the aligned API BOM update, no suppression. |
| log4j-api-2.24.3.jar | [CVE-2026-49844](https://logging.apache.org/security.html#CVE-2026-49844) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
| postgresql-42.7.11.jar | [CVE-2026-54291](https://github.com/pgjdbc/pgjdbc/security/advisories/GHSA-j92g-9f8w-j867) | HIGH | FIX NOW — compatible update above; subsequent scan verifies removal. |
| spring-core-6.2.19.jar | [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) | CRITICAL | FALSE POSITIVE — No XsltView, view resolver, or catch-all view-rendering controller; API controllers return JSON DTOs. |
| spring-core-6.2.19.jar | [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) | CRITICAL | FALSE POSITIVE — No SSE endpoints or streamed view fragments; all API responses are finite JSON. |
| spring-core-6.2.19.jar | [CVE-2026-47891](https://spring.io/security/cve-2026-47891/) | CRITICAL | FALSE POSITIVE — No WebFlux or Aalto dependency and no reactive XML request decoder. |
| spring-core-6.2.19.jar | [CVE-2026-47892](https://spring.io/security/cve-2026-47892/) | CRITICAL | FALSE POSITIVE — No WebFlux or functional RouterFunction endpoints; annotated servlet MVC only. |
| spring-core-6.2.19.jar | [CVE-2026-59313](https://spring.io/security/cve-2026-59313/) | CRITICAL | FALSE POSITIVE — No MVC functional endpoints or ServerResponse.sse; finite JSON responses only. |
| spring-core-6.2.19.jar | [CVE-2026-59283](https://spring.io/security/cve-2026-59283/) | CRITICAL | FALSE POSITIVE — No SimpleEvaluationContext or application SpEL parser; expression compilation is not enabled. |
| spring-core-6.2.19.jar | [CVE-2026-47885](https://spring.io/security/cve-2026-47885/) | HIGH | FALSE POSITIVE — No WebFlux, Flux<PartEvent>, or PartEventHttpMessageReader. |
| spring-core-6.2.19.jar | [CVE-2026-47886](https://spring.io/security/cve-2026-47886/) | HIGH | FALSE POSITIVE — No user-supplied SpEL evaluation; repository JPQL uses bound parameters. |
| spring-core-6.2.19.jar | [CVE-2026-47888](https://spring.io/security/cve-2026-47888/) | HIGH | FALSE POSITIVE — No RSocket dependency, listener, or RSocketMessageHandler. |
| spring-core-6.2.19.jar | [CVE-2026-47889](https://spring.io/security/cve-2026-47889/) | HIGH | FALSE POSITIVE — Embedded Tomcat servlet MVC, not Jetty Core reactive adapter; cookie flags tested. |
| spring-core-6.2.19.jar | [CVE-2026-47893](https://spring.io/security/cve-2026-47893/) | HIGH | FALSE POSITIVE — No WebFlux WebSocket service or WebSocket endpoints. |
| spring-core-6.2.19.jar | [CVE-2026-59282](https://spring.io/security/cve-2026-59282/) | HIGH | FALSE POSITIVE — Explicit JSON DTOs; no self-populating List properties. The sole InitBinder handles scalar asOf LocalDate only. |
| spring-core-6.2.19.jar | [CVE-2026-47883](https://spring.io/security/cve-2026-47883/) | MEDIUM | FALSE POSITIVE — No UrlHandlerFilter bean or configuration; safeReturnUrl handles SPA navigation. |
| spring-core-6.2.19.jar | [CVE-2026-47887](https://spring.io/security/cve-2026-47887/) | MEDIUM | FALSE POSITIVE — No UrlFileNameViewController or application view-name rendering. |
| spring-core-6.2.19.jar | [CVE-2026-59281](https://spring.io/security/cve-2026-59281/) | MEDIUM | FALSE POSITIVE — No EscapedErrors or server-rendered error HTML; safe ProblemDetail JSON and Angular safe copy. |
| spring-core-6.2.19.jar | [CVE-2026-59280](https://spring.io/security/cve-2026-59280/) | MEDIUM | FALSE POSITIVE — No FreeMarker dependency, SpringTemplateLoader, or user-controlled template/view names. |
| spring-core-6.2.19.jar | [CVE-2026-59314](https://spring.io/security/cve-2026-59314/) | LOW | FALSE POSITIVE — No ContentDisposition construction or user-supplied download filenames. |
| spring-data-jpa-3.5.13.jar | [CVE-2026-47834](https://spring.io/security/cve-2026-47834/) | MEDIUM | FALSE POSITIVE — No native SQL repository methods or user-controlled Sort/Pageable; fixed JPQL ordering. |
| Spring Security 6.5.11 (core and resource-server) | [CVE-2026-59270](https://spring.io/security/cve-2026-59270/) | CRITICAL | FALSE POSITIVE — No spring-security-ldap, UnboundID dependency, or embedded LDAP server. |
| Spring Security 6.5.11 (core and resource-server) | [CVE-2026-47841](https://spring.io/security/cve-2026-47841/) | HIGH | FALSE POSITIVE — No WebAuthn/passkeys or distributed HTTP session store; stateless Bearer JWT. |
| Spring Security 6.5.11 (core and resource-server) | [CVE-2026-47842](https://spring.io/security/cve-2026-47842/) | MEDIUM | FALSE POSITIVE — No AesBytesEncryptor usage; Argon2id passwords, HMAC JWT, SHA-256 opaque-token digests. |
| Spring Security 6.5.11 (core and resource-server) | [CVE-2026-59276](https://spring.io/security/cve-2026-59276/) | MEDIUM | FALSE POSITIVE — No DigestAuthenticationFilter, KeyBasedPersistenceTokenService, Password4j encoders, or OAuth2 authorization server. |
| Swagger UI 5.32.2 (both bundles) | [CVE-2026-49978](https://github.com/cure53/DOMPurify/security/advisories/GHSA-rp9w-3fw7-7cwq) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
| Swagger UI 5.32.2 (both bundles) | [CVE-2026-49458](https://github.com/cure53/DOMPurify/security/advisories/GHSA-hpcv-96wg-7vj8) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
| Swagger UI 5.32.2 (both bundles) | [CVE-2026-49459](https://github.com/cure53/DOMPurify/security/advisories/GHSA-r47g-fvhr-h676) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
| Swagger UI 5.32.2 (both bundles) | [CVE-2026-41240](https://github.com/cure53/DOMPurify/security/advisories/GHSA-h7mw-gpvr-xq4m) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
| Swagger UI 5.32.2 (both bundles) | [CVE-2026-65902](https://github.com/cure53/DOMPurify/security/advisories/GHSA-76mc-f452-cxcm) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
| Swagger UI 5.32.2 (both bundles) | [CVE-2026-65898](https://github.com/cure53/DOMPurify/security/advisories/GHSA-cmwh-pvxp-8882) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
| Swagger UI 5.32.2 (both bundles) | [CVE-2026-65899](https://github.com/cure53/DOMPurify/security/advisories/GHSA-vxr8-fq34-vvx9) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
| Swagger UI 5.32.2 (both bundles) | [CVE-2026-65900](https://github.com/cure53/DOMPurify/security/advisories/GHSA-gvmj-g25r-r7wr) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
| Swagger UI 5.32.2 (both bundles) | [CVE-2026-65901](https://github.com/cure53/DOMPurify/security/advisories/GHSA-x4vx-rjvf-j5p4) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
| Swagger UI 5.32.2 (both bundles) | [CVE-2026-65903](https://github.com/cure53/DOMPurify/security/advisories/GHSA-39q2-94rc-95cp) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
| Swagger UI 5.32.2 (both bundles) | [CVE-2026-66010](https://github.com/cure53/DOMPurify/security/advisories/GHSA-c2j3-45gr-mqc4) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
| Swagger UI 5.32.2 (both bundles) | [CVE-2026-41238](https://github.com/cure53/DOMPurify/releases/tag/3.4.0) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
| Swagger UI 5.32.2 (both bundles) | [CVE-2026-41239](https://github.com/cure53/DOMPurify/releases/tag/3.4.0) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
| Swagger UI 5.32.2 (both bundles) | [CVE-2026-75838](https://github.com/cure53/DOMPurify/releases/tag/3.4.13) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
| tomcat-embed-core-10.1.55.jar | [CVE-2026-65637](https://lists.apache.org/thread/djog953z1ohsyt25bdvhfzbmsy22vgcj) | CRITICAL | FIX NOW — compatible update above; subsequent scan verifies removal. |
| tomcat-embed-core-10.1.55.jar | [CVE-2026-65905](https://lists.apache.org/thread/9v114xlpgbzrrbzz5vf9f6r2q4wnxwwj) | CRITICAL | FIX NOW — compatible update above; subsequent scan verifies removal. |
| tomcat-embed-core-10.1.55.jar | [CVE-2026-53434](https://lists.apache.org/thread/x510lbq0sfrd1qyo7q3r1mpllgpdcosk) | CRITICAL | FIX NOW — compatible update above; subsequent scan verifies removal. |
| tomcat-embed-core-10.1.55.jar | [CVE-2026-55276](https://lists.apache.org/thread/jy09xjlzn6r2qwvqoph8vcmf959yq68v) | CRITICAL | FIX NOW — compatible update above; subsequent scan verifies removal. |
| tomcat-embed-core-10.1.55.jar | [CVE-2026-59083](https://lists.apache.org/thread/3g63zos2gkjo5vgnrk8kxmosv47w6wbq) | CRITICAL | FIX NOW — compatible update above; subsequent scan verifies removal. |
| tomcat-embed-core-10.1.55.jar | [CVE-2026-59084](https://lists.apache.org/thread/7w9746ootcxo0gvx26xjpw80l31f1qw7) | CRITICAL | FIX NOW — compatible update above; subsequent scan verifies removal. |
| tomcat-embed-core-10.1.55.jar | [CVE-2026-65182](https://lists.apache.org/thread/joosxvzc9b49ttj8lj0jw9mqt0ml767m) | CRITICAL | FIX NOW — compatible update above; subsequent scan verifies removal. |
| tomcat-embed-core-10.1.55.jar | [CVE-2026-68525](https://lists.apache.org/thread/x1y2lfsgzxwzc456f8954vbvgn03zhd7) | CRITICAL | FIX NOW — compatible update above; subsequent scan verifies removal. |
| tomcat-embed-core-10.1.55.jar | [CVE-2026-65183](https://lists.apache.org/thread/748o4st6d5dk6n3l7tgzo5yl68gg05c0) | HIGH | FIX NOW — compatible update above; subsequent scan verifies removal. |
| tomcat-embed-core-10.1.55.jar | [CVE-2026-66422](https://lists.apache.org/thread/j5plylz1b2vhqvbkqn7k58nygxhcpk73) | HIGH | FIX NOW — compatible update above; subsequent scan verifies removal. |
| tomcat-embed-core-10.1.55.jar | [CVE-2026-68569](https://lists.apache.org/thread/8robqo76q0osxgw0b5lcwgz0hcf9h4zc) | HIGH | FIX NOW — compatible update above; subsequent scan verifies removal. |
| tomcat-embed-core-10.1.55.jar | [CVE-2026-65927](https://lists.apache.org/thread/st1dx1zyn5y7ny2s0sscmh6lrv3worr4) | HIGH | FIX NOW — compatible update above; subsequent scan verifies removal. |
| tomcat-embed-core-10.1.55.jar | [CVE-2026-68763](https://lists.apache.org/thread/tv51ty39ppv41v04hdtkp9dp7tg02nzl) | HIGH | FIX NOW — compatible update above; subsequent scan verifies removal. |
| tomcat-embed-core-10.1.55.jar | [CVE-2026-53404](https://lists.apache.org/thread/rdhpghgfskrdmw9hqzjgjrtw538smpmz) | HIGH | FIX NOW — compatible update above; subsequent scan verifies removal. |
| tomcat-embed-core-10.1.55.jar | [CVE-2026-73180](https://lists.apache.org/thread/3j15vztszpyqss253mjq5v1kp7s6hooq) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
| tomcat-embed-core-10.1.55.jar | [CVE-2026-55955](https://lists.apache.org/thread/g4p5sf45p3f9r011pwqs9r54yd64s106) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
| tomcat-embed-core-10.1.55.jar | [CVE-2026-55956](https://lists.apache.org/thread/dcjdcnnnww9hhdm016hr0l7hpw1bzjfp) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
| tomcat-embed-core-10.1.55.jar | [CVE-2026-50229](https://lists.apache.org/thread/wlt2no8bw45zl1w8byop4zfqphldf5j0) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
| tomcat-embed-core-10.1.55.jar | [CVE-2026-66299](https://lists.apache.org/thread/8owczcc1o8qw1rxmg9gvfk4w2jnh4l5k) | MEDIUM | FIX NOW — compatible update above; subsequent scan verifies removal. |
