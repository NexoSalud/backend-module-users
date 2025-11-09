# reactive-nexo — Backend de ejemplo (Spring WebFlux + R2DBC)

Proyecto ejemplo para mostrar una API reactiva con Spring WebFlux y R2DBC.

Estado actual del proyecto
- Paquete raíz de la aplicación: `com.reactive.nexo`.
- Stack: Spring Boot 3.x, Spring WebFlux, Spring Data R2DBC.
- Bases de datos: soporta R2DBC (Postgres u otros drivers). Para pruebas y desarrollo ligero el proyecto usa H2 en memoria (configurable en `application.yml`).

Requisitos mínimos
- Java 17 o superior (en este repositorio se ha usado Java 19 en pruebas).
- Maven 3.6+

Cómo compilar y ejecutar

1. Compilar y ejecutar tests:

```bash
mvn clean test
```

2. Empaquetar la aplicación:

```bash
mvn clean package
```

3. Ejecutar la JAR producida:

```bash
java -jar target/reactive-nexo-0.0.1-SNAPSHOT.jar
```

Nota: la configuración de conexión a BD se controla desde `src/main/resources/application.yml`. Cambia el perfil o las propiedades para usar Postgres u otro R2DBC driver.

Qué hace esta versión
- Endpoints CRUD para usuarios en `/users` (reactivo).
- Endpoint extendido: GET `/users/{userId}` ahora devuelve, además de los datos del usuario, todos los atributos asociados y sus valores.
  - La representación es un DTO con la siguiente forma:

```json
{
  "id": 1,
  "name": "Juan Campo",
  "history_id": 0,
  "attributes": [
    { "attributeName": "fecha de nacimiento", "values": ["1992-05-06"] },
    { "attributeName": "lugar de nacimiento ciudad", "values": ["cali"] },
    ...
  ]
}
```

Datos de ejemplo (seed)
- Al iniciar con perfil distinto a `test`, el `UserInitializer` inserta usuarios de ejemplo y crea atributos y valores para cada usuario. Entre los atributos/valores insertados están:
  - fecha de nacimiento: `1992-05-06`
  - lugar de nacimiento ciudad: `cali`
  - lugar de nacimiento departamento: `valle`
  - lugar de nacimiento pais: `colombia`
  - ubicacion ciudad: `guachene`
  - ubicacion departamento: `cauca`
  - ubicacion pais: `colombia`
  - entidad de salud: `sura`
  - ultima consulta: `2024-06-06`
  - telefono: `315-000-0000`
  - email: `jhon-doe@test.co`
  - regimen: `subcidiado`
  - atributos ejemplo de historia clínica: `historia_clinica_numero`, `diagnostico_principal`, `alergias` (con valores de ejemplo)

Endpoints principales

- GET /users — lista todos los usuarios (Flux)
- POST /users — crea un usuario (Mono)
- GET /users/{userId} — devuelve user + atributos con valores (Mono<UserWithAttributesDTO>)
- PUT /users/{userId} — actualiza usuario
- DELETE /users/{userId} — elimina usuario
- GET /users/events — stream (SSE) de usuarios

- GET /users/by-identification/{identificationType}/{identificationNumber} — busca un usuario por tipo y número de identificación y devuelve el usuario más todos los atributos asociados y sus valores (Mono<UserWithAttributesDTO>). Ejemplo:

  - Solicitud (curl):

    ```bash
    curl -s http://localhost:8080/users/by-identification/CC/1 | jq .
    ```

  - Respuesta (ejemplo):

    ```json
    {
      "id": 1,
      "name": "Juan Campo",
      "identification_type": "CC",
      "identification_number": "1",
      "attributes": [
        { "attributeName": "fecha de nacimiento", "values": ["1992-05-06"] },
        { "attributeName": "lugar de nacimiento ciudad", "values": ["cali"] },
        { "attributeName": "telefono", "values": ["315-000-0000"] }
      ]
    }
    ```

Ver y probar el endpoint `/users/{userId}`

1. Levanta la aplicación (ver pasos arriba).
2. Llama al endpoint (ejemplo con curl):

```bash
curl -s http://localhost:8080/users/1 | jq .
```

Pruebas y desarrollo
- Los tests del proyecto están configurados para ejecutarse con H2 (perfil `test`). Para ejecutar la suite de pruebas:

```bash
mvn test
```

Si quieres cambiar a Postgres durante el desarrollo, actualiza `application.yml` o utiliza un perfil específico que apunte a tu instancia Postgres con el driver R2DBC apropiado.

Notas técnicas rápidas
- Modelos relevantes: `User`, `AttributeUser`, `ValueAttributeUser`.
- Repositorios reactivos: `UserRepository`, `AttributeUserRepository`, `ValueAttributeUserRepository`.
- Servicio que compone la respuesta con atributos/valores: `UserService.getUserWithAttributes(userId)`.
- Inicializador de datos: `UserInitializer` (inserta usuarios, atributos y valores de ejemplo).

Si quieres, añadimos un test de integración que verifique la estructura JSON devuelta por `/users/{userId}` y los valores semilla. Puedo implementarlo y ejecutarlo automáticamente.
