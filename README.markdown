# sqlToJPA

`sqlToJPA` is a tool for automatically generating Java JPA (Java Persistence API) entity classes from SQL schema files. It parses `CREATE TABLE` statements, maps SQL data types to Java types, and creates fully annotated Java classes with fields, getters, setters, constructors, relationships, and `equals`/`hashCode` methods. The project includes three implementations:

- **sqlToJPAPython**: A Python-based implementation (`sqlToJPAPython/` directory).
- **sqlToJPAJava**: A Java-based implementation (`sqlToJPAJava/` directory).
- **sqlToJPAcpp**: A C++-based implementation (`sqlToJPAcpp/` directory).

All implementations produce identical Java JPA entity classes, offering flexibility to use Python, Java, or C++ depending on your environment or preference.

## Features
- Converts SQL data types to appropriate Java types (e.g., `BIGINT` to `Long`, `VARCHAR` to `String`, `TIMESTAMP` to `LocalDateTime`).
- Supports primary keys (`@Id`, `@GeneratedValue`), foreign keys (`@ManyToOne`), and constraints like `NOT NULL`.
- Handles `AUTO_INCREMENT` and default values in the generated entities.
- Generates each entity in a separate `.java` file under the specified package and output directory.
- Includes proper JPA annotations (`@Entity`, `@Table`, `@Column`, `@JoinColumn`, etc.).
- Supports relationships between tables based on foreign key constraints.

## Workflow Diagram
The following Mermaid diagram illustrates the workflow of the `sqlToJPA` tool, showing how each implementation processes an SQL schema file to produce Java JPA entity classes:

```mermaid
graph LR
    A[SQL Schema File<br/>schema.sql] -->|Lê o SQL de| B(sqlToJPA<br/>Java Application)
    B -->|Gera Entidades JPA| C[Java JPA Entities<br/>e.g., Customer.java, Product.java]
    C -->|Salva em| D[Diretório de Pacote<br/>e.g., src/main/java/com/example/entities]
```

## Requirements
### For sqlToJPAPython
- Python 3.6 or higher (uses `pathlib`, `dataclasses`, and type hints).
- No external Python dependencies required.
- Generated Java classes require Java 8 or higher (due to `java.time` classes like `LocalDateTime`) and JPA (`javax.persistence`) for compilation.

### For sqlToJPAJava
- Java 8 or higher (uses `java.time` classes like `LocalDateTime`).
- No external dependencies beyond the standard Java library and JPA (`javax.persistence`).

### For sqlToJPAcpp
- C++17 or higher (uses `<filesystem>` and other C++17 features).
- A C++ compiler supporting C++17 (e.g., g++ 7.0 or later).
- No external C++ dependencies required.
- Generated Java classes require Java 8 or higher and JPA (`javax.persistence`) for compilation.

### General
- A SQL schema file containing `CREATE TABLE` statements.

## Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/mmaunze/sqlToJPA.git
   cd sqlToJPA
   ```
2. Ensure you have the required tools installed:
   - Python 3.6+ for `sqlToJPAPython`.
   - JDK 8+ for `sqlToJPAJava`.
   - C++17-compliant compiler (e.g., g++) for `sqlToJPAcpp`.
3. Place your SQL schema file (e.g., `schema.sql`) in the project directory or a subdirectory.

## Usage
The tool can be run using any of the three implementations (Python, Java, or C++). All produce identical Java JPA entity classes.

### Using sqlToJPAPython
1. Navigate to the Python implementation directory:
   ```bash
   cd sqlToJPAPython
   ```
2. Run the generator:
   ```bash
   python sql_parser_jpa_generator.py schema.sql com.example.entities ./generated-classes
   ```
   - `schema.sql`: Path to the input SQL schema file.
   - `com.example.entities`: Package name for the generated Java classes (optional, defaults to `com.example.entities`).
   - `./generated-classes`: Output directory for the generated files (optional, defaults to `./generated-entities`).

### Using sqlToJPAJava
1. Navigate to the Java implementation directory:
   ```bash
   cd sqlToJPAJava
   ```
2. Compile the Java source file:
   ```bash
   javac SQLParserJPAGenerator.java
   ```
3. Run the generator:
   ```bash
   java SQLParserJPAGenerator schema.sql com.example.entities ./generated-classes
   ```
   - Parameters are the same as for the Python version.

### Using sqlToJPAcpp
1. Navigate to the C++ implementation directory:
   ```bash
   cd sqlToJPAcpp
   ```
2. Compile the C++ source file:
   ```bash
   g++ -std=c++17 sql_parser_jpa_generator.cpp -o sql_parser_jpa_generator
   ```
3. Run the generator:
   ```bash
   ./sql_parser_jpa_generator schema.sql com.example.entities ./generated-classes
   ```
   - Parameters are the same as for the Python and Java versions.

### Output
All implementations create a directory structure matching the package name (e.g., `./generated-classes/com/example/entities`) and generate one `.java` file per table.

## Example SQL Schema
Create a file named `schema.sql` with the following content to test any implementation:
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

Run any implementation, e.g., for C++:
```bash
cd sqlToJPAcpp
g++ -std=c++17 sql_parser_jpa_generator.cpp -o sql_parser_jpa_generator
./sql_parser_jpa_generator ../schema.sql com.example.entities ../generated-classes
```

Or for Python:
```bash
cd sqlToJPAPython
python sql_parser_jpa_generator.py ../schema.sql com.example.entities ../generated-classes
```

Or for Java:
```bash
cd sqlToJPAJava
javac SQLParserJPAGenerator.java
java SQLParserJPAGenerator ../schema.sql com.example.entities ../generated-classes
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
- The generated Java classes require a Java environment with JPA to compile and run.

## Contributing
Contributions are welcome! Feel free to submit issues or pull requests to improve any implementation, such as adding support for more SQL types, relationship types, or annotations like Lombok or JPA validation. Please specify whether your contribution targets `sqlToJPAPython`, `sqlToJPAJava`, `sqlToJPAcpp`, or multiple implementations.

## License
This project is licensed under the MIT License.