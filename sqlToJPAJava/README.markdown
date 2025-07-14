# SQLParserJPAGenerator

SQLParserJPAGenerator is a Java tool that automatically generates JPA (Java Persistence API) entity classes from SQL schema files. It parses `CREATE TABLE` statements, maps SQL data types to Java types, and creates fully annotated Java classes with fields, getters, setters, constructors, relationships, and `equals`/`hashCode` methods.

## Features
- Converts SQL data types to appropriate Java types (e.g., `BIGINT` to `Long`, `VARCHAR` to `String`, `TIMESTAMP` to `LocalDateTime`).
- Supports primary keys (`@Id`, `@GeneratedValue`), foreign keys (`@ManyToOne`), and constraints like `NOT NULL`.
- Handles `AUTO_INCREMENT` and default values in the generated entities.
- Generates each entity in a separate `.java` file under the specified package and output directory.
- Includes proper JPA annotations (`@Entity`, `@Table`, `@Column`, `@JoinColumn`, etc.).
- Supports relationships between tables based on foreign key constraints.

## Requirements
- Java 8 or higher (uses `java.time` classes like `LocalDateTime`).
- A SQL schema file containing `CREATE TABLE` statements.
- No external dependencies beyond the standard Java library and JPA (`javax.persistence`).

## Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/mmaunze/sqlToJPA.git
   cd sqlToJPA/sqlToJPAJava
   ```
2. Ensure you have a Java Development Kit (JDK) installed (version 8 or higher).
3. Place your SQL schema file (e.g., `schema.sql`) in the project directory.

## Usage
1. Compile the `SQLParserJPAGenerator.java` file:
   ```bash
   javac SQLParserJPAGenerator.java
   ```
2. Run the generator with the following command:
   ```bash
   java SQLParserJPAGenerator schema.sql com.example.entities ./generated-classes
   ```
   - `schema.sql`: Path to the input SQL schema file.
   - `com.example.entities`: Package name for the generated Java classes (optional, defaults to `com.example.entities`).
   - `./generated-classes`: Output directory for the generated files (optional, defaults to `./generated-entities`).

3. The tool will create a directory structure matching the package name (e.g., `./generated-classes/com/example/entities`) and generate one `.java` file per table.

## Example SQL Schema
Create a file named `schema.sql` with the following content to test the generator:
```sql
CREATE TABLE users (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    role_id INT,
    FOREIGN KEY (role_id) REFERENCES roles(id)
);

CREATE TABLE roles (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL
);
```

Run the tool:
```bash
javac SQLParserJPAGenerator.java
java SQLParserJPAGenerator schema.sql com.example.entities ./generated-classes
```

This will generate two files:
- `./generated-classes/com/example/entities/Roles.java`
- `./generated-classes/com/example/entities/Users.java`

### Generated Output
- `Roles.java`: Contains an entity with `Integer id` and `String name`, with proper `@Id`, `@GeneratedValue`, and `nullable = false` annotations.
- `Users.java`: Contains an entity with `Long id`, `String username`, `String email`, `LocalDateTime createdAt`, `Integer roleId`, and a `@ManyToOne` relationship to `Roles`.

## Output Example
### Roles.java
```java
package com.example.entities;

import java.io.Serializable;
import javax.persistence.*;

@Entity
@Table(name = "roles")
public class Roles implements Serializable {
    // ... (fields, getters, setters, etc.)
}
```

### Users.java
```java
package com.example.entities;

import java.io.Serializable;
import java.time.LocalDateTime;
import javax.persistence.*;

@Entity
@Table(name = "users")
public class Users implements Serializable {
    // ... (fields, relationships, getters, setters, etc.)
}
```

## Notes
- The tool assumes standard SQL syntax for `CREATE TABLE` statements. Complex schemas with non-standard syntax may require adjustments.
- Foreign key relationships are mapped as `@ManyToOne`. Support for other relationship types (e.g., `@OneToMany`) may be added in future versions.
- If a column definition cannot be parsed, a warning is logged to the console, and the column is skipped.
- The output directory and package structure are created automatically if they don't exist.

## Contributing
Feel free to submit issues or pull requests to improve the tool, such as adding support for more SQL types, relationship types, or annotations like Lombok or JPA validation.

## License
This project is licensed under the MIT License.