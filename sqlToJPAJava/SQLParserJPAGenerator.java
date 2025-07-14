import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SQLParserJPAGenerator {
    
    private static final Map<String, String> SQL_TO_JAVA_TYPE_MAP = new HashMap<>();
    private static final Map<String, String> IMPORT_MAP = new HashMap<>();
    
    static {
        // Mapeamento de tipos SQL para Java
        SQL_TO_JAVA_TYPE_MAP.put("VARCHAR", "String");
        SQL_TO_JAVA_TYPE_MAP.put("CHAR", "String");
        SQL_TO_JAVA_TYPE_MAP.put("TEXT", "String");
        SQL_TO_JAVA_TYPE_MAP.put("LONGTEXT", "String");
        SQL_TO_JAVA_TYPE_MAP.put("MEDIUMTEXT", "String");
        SQL_TO_JAVA_TYPE_MAP.put("TINYTEXT", "String");
        SQL_TO_JAVA_TYPE_MAP.put("CLOB", "String");
        SQL_TO_JAVA_TYPE_MAP.put("NVARCHAR", "String");
        SQL_TO_JAVA_TYPE_MAP.put("NCHAR", "String");
        SQL_TO_JAVA_TYPE_MAP.put("NTEXT", "String");
        
        SQL_TO_JAVA_TYPE_MAP.put("INT", "Integer");
        SQL_TO_JAVA_TYPE_MAP.put("INTEGER", "Integer");
        SQL_TO_JAVA_TYPE_MAP.put("SMALLINT", "Short");
        SQL_TO_JAVA_TYPE_MAP.put("TINYINT", "Byte");
        SQL_TO_JAVA_TYPE_MAP.put("BIGINT", "Long");
        SQL_TO_JAVA_TYPE_MAP.put("MEDIUMINT", "Integer");
        
        SQL_TO_JAVA_TYPE_MAP.put("DECIMAL", "BigDecimal");
        SQL_TO_JAVA_TYPE_MAP.put("NUMERIC", "BigDecimal");
        SQL_TO_JAVA_TYPE_MAP.put("MONEY", "BigDecimal");
        SQL_TO_JAVA_TYPE_MAP.put("SMALLMONEY", "BigDecimal");
        SQL_TO_JAVA_TYPE_MAP.put("FLOAT", "Float");
        SQL_TO_JAVA_TYPE_MAP.put("REAL", "Float");
        SQL_TO_JAVA_TYPE_MAP.put("DOUBLE", "Double");
        
        SQL_TO_JAVA_TYPE_MAP.put("DATE", "LocalDate");
        SQL_TO_JAVA_TYPE_MAP.put("TIME", "LocalTime");
        SQL_TO_JAVA_TYPE_MAP.put("TIMESTAMP", "LocalDateTime");
        SQL_TO_JAVA_TYPE_MAP.put("DATETIME", "LocalDateTime");
        SQL_TO_JAVA_TYPE_MAP.put("DATETIME2", "LocalDateTime");
        SQL_TO_JAVA_TYPE_MAP.put("SMALLDATETIME", "LocalDateTime");
        
        SQL_TO_JAVA_TYPE_MAP.put("BOOLEAN", "Boolean");
        SQL_TO_JAVA_TYPE_MAP.put("BOOL", "Boolean");
        SQL_TO_JAVA_TYPE_MAP.put("BIT", "Boolean");
        
        SQL_TO_JAVA_TYPE_MAP.put("BLOB", "byte[]");
        SQL_TO_JAVA_TYPE_MAP.put("LONGBLOB", "byte[]");
        SQL_TO_JAVA_TYPE_MAP.put("MEDIUMBLOB", "byte[]");
        SQL_TO_JAVA_TYPE_MAP.put("TINYBLOB", "byte[]");
        SQL_TO_JAVA_TYPE_MAP.put("BINARY", "byte[]");
        SQL_TO_JAVA_TYPE_MAP.put("VARBINARY", "byte[]");
        SQL_TO_JAVA_TYPE_MAP.put("IMAGE", "byte[]");
        
        SQL_TO_JAVA_TYPE_MAP.put("JSON", "String");
        SQL_TO_JAVA_TYPE_MAP.put("JSONB", "String");
        SQL_TO_JAVA_TYPE_MAP.put("XML", "String");
        SQL_TO_JAVA_TYPE_MAP.put("UUID", "UUID");
        
        // Mapeamento de imports necessários
        IMPORT_MAP.put("BigDecimal", "java.math.BigDecimal");
        IMPORT_MAP.put("BigInteger", "java.math.BigInteger");
        IMPORT_MAP.put("LocalDate", "java.time.LocalDate");
        IMPORT_MAP.put("LocalTime", "java.time.LocalTime");
        IMPORT_MAP.put("LocalDateTime", "java.time.LocalDateTime");
        IMPORT_MAP.put("UUID", "java.util.UUID");
        IMPORT_MAP.put("Objects", "java.util.Objects");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: java SQLParserJPAGenerator <caminho_ficheiro_sql> [pacote_destino] [directorio_saida]");
            System.out.println("Exemplo: java SQLParserJPAGenerator schema.sql com.example.entities ./src/main/java");
            return;
        }
        
        String sqlFilePath = args[0];
        String packageName = args.length > 1 ? args[1] : "com.example.entities";
        String outputDir = args.length > 2 ? args[2] : "./generated-entities";
        
        try {
            SQLParserJPAGenerator generator = new SQLParserJPAGenerator();
            generator.generateEntitiesFromSQL(sqlFilePath, packageName, outputDir);
        } catch (Exception e) {
            System.err.println("Erro ao gerar entidades: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void generateEntitiesFromSQL(String sqlFilePath, String packageName, String outputDir) throws IOException {
        // Criar directório de saída
        File outputDirectory = new File(outputDir);
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }
        
        String sqlContent = new String(Files.readAllBytes(Paths.get(sqlFilePath)));
        
        // Limpar e normalizar o SQL
        sqlContent = cleanSQL(sqlContent);
        
        // Extrair informações das tabelas
        List<TableInfo> tables = parseSQL(sqlContent);
        
        // Processar relacionamentos
        processRelationships(tables, sqlContent);
        
        // Gerar classes
        for (TableInfo table : tables) {
            generateEntityClass(table, packageName, outputDir);
        }
        
        System.out.println("Geração concluída! " + tables.size() + " entidades criadas em: " + outputDir);
    }
    
    private String cleanSQL(String sqlContent) {
        // Remove comentários de linha
        sqlContent = sqlContent.replaceAll("--.*$", "");
        
        // Remove comentários de bloco
        sqlContent = sqlContent.replaceAll("/\\*[\\s\\S]*?\\*/", "");
        
        // Normalizar espaços
        sqlContent = sqlContent.replaceAll("\\s+", " ");
        
        return sqlContent;
    }
    
    private List<TableInfo> parseSQL(String sqlContent) {
        List<TableInfo> tables = new ArrayList<>();
        
        // Regex melhorada para CREATE TABLE
        Pattern createTablePattern = Pattern.compile(
            "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:`([^`]+)`|([\\w_]+))\\s*\\((.*?)\\)\\s*(?:ENGINE|DEFAULT|COMMENT|;|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        
        Matcher matcher = createTablePattern.matcher(sqlContent);
        
        while (matcher.find()) {
            String tableName = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            String tableDefinition = matcher.group(3);
            
            if (tableName != null && !tableName.trim().isEmpty()) {
                TableInfo table = new TableInfo();
                table.name = tableName.trim();
                table.className = toCamelCase(table.name);
                table.columns = parseColumns(tableDefinition);
                table.primaryKeys = findPrimaryKeys(tableDefinition);
                table.foreignKeys = findForeignKeys(tableDefinition);
                
                // Marcar colunas como chave primária
                for (String pkColumn : table.primaryKeys) {
                    table.columns.stream()
                        .filter(col -> col.name.equals(pkColumn))
                        .findFirst()
                        .ifPresent(col -> col.primaryKey = true);
                }
                
                tables.add(table);
                System.out.println("Tabela encontrada: " + table.name + " (" + table.columns.size() + " colunas)");
            }
        }
        
        return tables;
    }
    
    private List<ColumnInfo> parseColumns(String tableDefinition) {
        List<ColumnInfo> columns = new ArrayList<>();
        
        // Dividir por vírgulas, mas ignorar vírgulas dentro de parênteses
        String[] lines = splitByCommaIgnoringParentheses(tableDefinition);
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || isConstraintLine(line)) {
                continue;
            }
            
            ColumnInfo column = parseColumn(line);
            if (column != null) {
                columns.add(column);
            }
        }
        
        return columns;
    }
    
    private String[] splitByCommaIgnoringParentheses(String input) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parenthesesCount = 0;
        
        for (char c : input.toCharArray()) {
            if (c == '(') {
                parenthesesCount++;
            } else if (c == ')') {
                parenthesesCount--;
            } else if (c == ',' && parenthesesCount == 0) {
                result.add(current.toString().trim());
                current = new StringBuilder();
                continue;
            }
            current.append(c);
        }
        
        if (current.length() > 0) {
            result.add(current.toString().trim());
        }
        
        return result.toArray(new String[0]);
    }
    
    private boolean isConstraintLine(String line) {
        String upperLine = line.toUpperCase();
        return upperLine.startsWith("PRIMARY KEY") || 
               upperLine.startsWith("FOREIGN KEY") || 
               upperLine.startsWith("KEY") ||
               upperLine.startsWith("INDEX") || 
               upperLine.startsWith("UNIQUE") ||
               upperLine.startsWith("CONSTRAINT") ||
               upperLine.startsWith("CHECK");
    }
    
    private ColumnInfo parseColumn(String columnDefinition) {
        // Regex mais robusta para parsing de colunas
        Pattern columnPattern = Pattern.compile(
            "(?:`([^`]+)`|([\\w_]+))\\s+" +                           // nome da coluna
            "([\\w()\\s,]+?)\\s*" +                                   // tipo
            "(?:\\s+(NOT\\s+NULL|NULL))?\\s*" +                       // nullability
            "(?:\\s+(AUTO_INCREMENT|IDENTITY))?\\s*" +                // auto increment
            "(?:\\s+(PRIMARY\\s+KEY))?\\s*" +                         // primary key
            "(?:\\s+DEFAULT\\s+([^,\\s]+))?\\s*" +                    // default value
            "(?:\\s+COMMENT\\s+['\"]([^'\"]*)['\"])?",               // comment
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = columnPattern.matcher(columnDefinition);
        
        if (matcher.find()) {
            ColumnInfo column = new ColumnInfo();
            column.name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            column.fieldName = toCamelCase(column.name, false);
            
            String sqlType = matcher.group(3).trim().toUpperCase();
            column.sqlType = extractBaseType(sqlType);
            column.javaType = convertSQLTypeToJava(column.sqlType);
            
            // Verificar se é unsigned
            if (sqlType.contains("UNSIGNED")) {
                column.javaType = adjustForUnsigned(column.javaType);
            }
            
            String nullability = matcher.group(4);
            column.nullable = nullability == null || nullability.toUpperCase().contains("NULL");
            
            String autoIncrement = matcher.group(5);
            column.autoIncrement = autoIncrement != null;
            
            String primaryKey = matcher.group(6);
            column.primaryKey = primaryKey != null;
            
            String defaultValue = matcher.group(7);
            column.defaultValue = defaultValue;
            
            return column;
        } else {
            System.err.println("Aviso: Não foi possível parsear a definição da coluna: " + columnDefinition);
            return null;
        }
    }
    
    private String extractBaseType(String sqlType) {
        // Extrair tipo base (sem parâmetros)
        int parenIndex = sqlType.indexOf('(');
        if (parenIndex != -1) {
            sqlType = sqlType.substring(0, parenIndex);
        }
        
        // Remover UNSIGNED se presente
        sqlType = sqlType.replaceAll("\\s+UNSIGNED", "");
        
        return sqlType.trim();
    }
    
    private String adjustForUnsigned(String javaType) {
        // Ajustar tipos para unsigned
        switch (javaType) {
            case "Byte":
                return "Short";
            case "Short":
                return "Integer";
            case "Integer":
                return "Long";
            case "Long":
                return "BigInteger";
            default:
                return javaType;
        }
    }
    
    private List<String> findPrimaryKeys(String tableDefinition) {
        List<String> primaryKeys = new ArrayList<>();
        
        Pattern pkPattern = Pattern.compile(
            "PRIMARY\\s+KEY\\s*\\(([^)]+)\\)",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = pkPattern.matcher(tableDefinition);
        if (matcher.find()) {
            String pkColumns = matcher.group(1);
            String[] columns = pkColumns.split(",");
            for (String column : columns) {
                column = column.trim().replaceAll("`", "");
                primaryKeys.add(column);
            }
        }
        
        return primaryKeys;
    }
    
    private List<ForeignKeyInfo> findForeignKeys(String tableDefinition) {
        List<ForeignKeyInfo> foreignKeys = new ArrayList<>();
        
        Pattern fkPattern = Pattern.compile(
            "FOREIGN\\s+KEY\\s*\\(([^)]+)\\)\\s*REFERENCES\\s+(?:`([^`]+)`|([\\w_]+))\\s*\\(([^)]+)\\)",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = fkPattern.matcher(tableDefinition);
        while (matcher.find()) {
            ForeignKeyInfo fk = new ForeignKeyInfo();
            fk.columnName = matcher.group(1).trim().replaceAll("`", "");
            fk.referencedTable = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            fk.referencedColumn = matcher.group(4).trim().replaceAll("`", "");
            
            foreignKeys.add(fk);
        }
        
        return foreignKeys;
    }
    
    private void processRelationships(List<TableInfo> tables, String sqlContent) {
        // Processar relacionamentos entre tabelas
        Map<String, TableInfo> tableMap = new HashMap<>();
        for (TableInfo table : tables) {
            tableMap.put(table.name, table);
        }
        
        for (TableInfo table : tables) {
            for (ForeignKeyInfo fk : table.foreignKeys) {
                if (tableMap.containsKey(fk.referencedTable)) {
                    fk.referencedTableInfo = tableMap.get(fk.referencedTable);
                }
            }
        }
    }
    
    private String convertSQLTypeToJava(String sqlType) {
        return SQL_TO_JAVA_TYPE_MAP.getOrDefault(sqlType, "String");
    }
    
    private void generateEntityClass(TableInfo table, String packageName, String outputDir) throws IOException {
        StringBuilder sb = new StringBuilder();
        
        // Determinar imports necessários
        Set<String> imports = new HashSet<>();
        imports.add("javax.persistence.*");
        imports.add("java.io.Serializable");
        
        for (ColumnInfo column : table.columns) {
            if (IMPORT_MAP.containsKey(column.javaType)) {
                imports.add(IMPORT_MAP.get(column.javaType));
            }
        }
        
        // Package
        sb.append("package ").append(packageName).append(";\n\n");
        
        // Imports
        for (String imp : imports.stream().sorted().toArray(String[]::new)) {
            sb.append("import ").append(imp).append(";\n");
        }
        sb.append("\n");
        
        // Documentação da classe
        sb.append("/**\n");
        sb.append(" * Entidade JPA para a tabela ").append(table.name).append("\n");
        sb.append(" * Gerada automaticamente pelo SQLParserJPAGenerator\n");
        sb.append(" */\n");
        
        // Anotações da classe
        sb.append("@Entity\n");
        sb.append("@Table(name = \"").append(table.name).append("\")\n");
        sb.append("public class ").append(table.className).append(" implements Serializable {\n\n");
        
        sb.append("    private static final long serialVersionUID = 1L;\n\n");
        
        // Campos
        for (ColumnInfo column : table.columns) {
            generateField(sb, column);
        }
        
        // Relacionamentos
        for (ForeignKeyInfo fk : table.foreignKeys) {
            if (fk.referencedTableInfo != null) {
                generateRelationshipField(sb, fk);
            }
        }
        
        // Construtores
        generateConstructors(sb, table);
        
        // Getters e Setters
        for (ColumnInfo column : table.columns) {
            generateGetterSetter(sb, column);
        }
        
        for (ForeignKeyInfo fk : table.foreignKeys) {
            if (fk.referencedTableInfo != null) {
                generateRelationshipGetterSetter(sb, fk);
            }
        }
        
        // equals e hashCode
        generateEqualsHashCode(sb, table);
        
        // toString
        generateToString(sb, table);
        
        sb.append("}\n");
        
        // Escrever ficheiro
        String fileName = table.className + ".java";
        File file = new File(outputDir, fileName);
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.print(sb.toString());
        }
        
        System.out.println("Entidade gerada: " + fileName);
    }
    
    private void generateField(StringBuilder sb, ColumnInfo column) {
        // Comentário
        sb.append("    /**\n");
        sb.append("     * Campo ").append(column.name);
        if (column.defaultValue != null) {
            sb.append(" (default: ").append(column.defaultValue).append(")");
        }
        sb.append("\n     */\n");
        
        // Anotações
        if (column.primaryKey) {
            sb.append("    @Id\n");
            if (column.autoIncrement) {
                sb.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
            }
        }
        
        sb.append("    @Column(name = \"").append(column.name).append("\"");
        if (!column.nullable) {
            sb.append(", nullable = false");
        }
        if (column.defaultValue != null && !column.primaryKey) {
            sb.append(", columnDefinition = \"").append(column.sqlType);
            if (!column.nullable) {
                sb.append(" NOT NULL");
            }
            sb.append(" DEFAULT ").append(column.defaultValue).append("\"");
        }
        sb.append(")\n");
        
        // Declaração do campo
        sb.append("    private ").append(column.javaType).append(" ").append(column.fieldName).append(";\n\n");
    }
    
    private void generateRelationshipField(StringBuilder sb, ForeignKeyInfo fk) {
        String referencedClassName = fk.referencedTableInfo.className;
        String fieldName = toCamelCase(fk.referencedTable, false);
        
        sb.append("    /**\n");
        sb.append("     * Relacionamento com ").append(fk.referencedTable).append("\n");
        sb.append("     */\n");
        sb.append("    @ManyToOne(fetch = FetchType.LAZY)\n");
        sb.append("    @JoinColumn(name = \"").append(fk.columnName).append("\")\n");
        sb.append("    private ").append(referencedClassName).append(" ").append(fieldName).append(";\n\n");
    }
    
    private void generateConstructors(StringBuilder sb, TableInfo table) {
        // Construtor vazio
        sb.append("    /**\n");
        sb.append("     * Construtor vazio\n");
        sb.append("     */\n");
        sb.append("    public ").append(table.className).append("() {\n");
        sb.append("    }\n\n");
        
        // Construtor com campos obrigatórios
        List<ColumnInfo> requiredColumns = table.columns.stream()
            .filter(col -> !col.nullable && !col.autoIncrement)
            .collect(java.util.stream.Collectors.toList());
        
        if (!requiredColumns.isEmpty()) {
            sb.append("    /**\n");
            sb.append("     * Construtor com campos obrigatórios\n");
            sb.append("     */\n");
            sb.append("    public ").append(table.className).append("(");
            
            for (int i = 0; i < requiredColumns.size(); i++) {
                ColumnInfo col = requiredColumns.get(i);
                sb.append(col.javaType).append(" ").append(col.fieldName);
                if (i < requiredColumns.size() - 1) {
                    sb.append(", ");
                }
            }
            
            sb.append(") {\n");
            
            for (ColumnInfo col : requiredColumns) {
                sb.append("        this.").append(col.fieldName).append(" = ").append(col.fieldName).append(";\n");
            }
            
            sb.append("    }\n\n");
        }
    }
    
    private void generateGetterSetter(StringBuilder sb, ColumnInfo column) {
        String capitalizedFieldName = capitalizeFirst(column.fieldName);
        
        // Getter
        sb.append("    public ").append(column.javaType).append(" get").append(capitalizedFieldName).append("() {\n");
        sb.append("        return ").append(column.fieldName).append(";\n");
        sb.append("    }\n\n");
        
        // Setter
        sb.append("    public void set").append(capitalizedFieldName).append("(").append(column.javaType).append(" ").append(column.fieldName).append(") {\n");
        sb.append("        this.").append(column.fieldName).append(" = ").append(column.fieldName).append(";\n");
        sb.append("    }\n\n");
    }
    
    private void generateRelationshipGetterSetter(StringBuilder sb, ForeignKeyInfo fk) {
        String referencedClassName = fk.referencedTableInfo.className;
        String fieldName = toCamelCase(fk.referencedTable, false);
        String capitalizedFieldName = capitalizeFirst(fieldName);
        
        // Getter
        sb.append("    public ").append(referencedClassName).append(" get").append(capitalizedFieldName).append("() {\n");
        sb.append("        return ").append(fieldName).append(";\n");
        sb.append("    }\n\n");
        
        // Setter
        sb.append("    public void set").append(capitalizedFieldName).append("(").append(referencedClassName).append(" ").append(fieldName).append(") {\n");
        sb.append("        this.").append(fieldName).append(" = ").append(fieldName).append(";\n");
        sb.append("    }\n\n");
    }
    
    private void generateEqualsHashCode(StringBuilder sb, TableInfo table) {
        List<ColumnInfo> pkColumns = table.columns.stream()
            .filter(col -> col.primaryKey)
            .collect(java.util.stream.Collectors.toList());
        
        if (!pkColumns.isEmpty()) {
            sb.append("    @Override\n");
            sb.append("    public boolean equals(Object o) {\n");
            sb.append("        if (this == o) return true;\n");
            sb.append("        if (o == null || getClass() != o.getClass()) return false;\n");
            sb.append("        ").append(table.className).append(" that = (").append(table.className).append(") o;\n");
            sb.append("        return ");
            
            for (int i = 0; i < pkColumns.size(); i++) {
                ColumnInfo col = pkColumns.get(i);
                sb.append("Objects.equals(").append(col.fieldName).append(", that.").append(col.fieldName).append(")");
                if (i < pkColumns.size() - 1) {
                    sb.append(" && ");
                }
            }
            
            sb.append(";\n");
            sb.append("    }\n\n");
            
            sb.append("    @Override\n");
            sb.append("    public int hashCode() {\n");
            sb.append("        return Objects.hash(");
            
            for (int i = 0; i < pkColumns.size(); i++) {
                ColumnInfo col = pkColumns.get(i);
                sb.append(col.fieldName);
                if (i < pkColumns.size() - 1) {
                    sb.append(", ");
                }
            }
            
            sb.append(");\n");
            sb.append("    }\n\n");
        }
    }
    
    private void generateToString(StringBuilder sb, TableInfo table) {
        sb.append("    @Override\n");
        sb.append("    public String toString() {\n");
        sb.append("        return \"").append(table.className).append("{\" +\n");
        
        for (int i = 0; i < table.columns.size(); i++) {
            ColumnInfo column = table.columns.get(i);
            sb.append("                \"").append(column.fieldName).append("=\" + ").append(column.fieldName);
            if (i < table.columns.size() - 1) {
                sb.append(" + \", \" +\n");
            } else {
                sb.append(" +\n");
            }
        }
        
        sb.append("                '}';\n");
        sb.append("    }\n");
    }
    
    private String toCamelCase(String input) {
        return toCamelCase(input, true);
    }
    
    private String toCamelCase(String input, boolean capitalizeFirst) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = capitalizeFirst;
        
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            
            if (c == '_' || c == '-') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        
        return result.toString();
    }
    
    private String capitalizeFirst(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }
    
    // Classes auxiliares
    static class TableInfo {
        String name;
        String className;
        List<ColumnInfo> columns = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();
        List<ForeignKeyInfo> foreignKeys = new ArrayList<>();
    }
    
    static class ColumnInfo {
        String name;
        String fieldName;
        String sqlType;
        String javaType;
        boolean nullable = true;
        boolean primaryKey = false;
        boolean autoIncrement = false;
        String defaultValue;
    }
    
    static class ForeignKeyInfo {
        String columnName;
        String referencedTable;
        String referencedColumn;
        TableInfo referencedTableInfo;
    }
}