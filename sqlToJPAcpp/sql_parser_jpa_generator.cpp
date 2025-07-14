#include <iostream>
#include <fstream>
#include <sstream>
#include <regex>
#include <filesystem>
#include <map>
#include <set>
#include <vector>
#include <algorithm>
#include <cctype>

namespace fs = std::filesystem;

// Mapeamento de tipos SQL para Java
static const std::map<std::string, std::string> SQL_TO_JAVA_TYPE_MAP = {
    {"VARCHAR", "String"}, {"CHAR", "String"}, {"TEXT", "String"}, {"LONGTEXT", "String"},
    {"MEDIUMTEXT", "String"}, {"TINYTEXT", "String"}, {"CLOB", "String"}, {"NVARCHAR", "String"},
    {"NCHAR", "String"}, {"NTEXT", "String"},
    {"INT", "Integer"}, {"INTEGER", "Integer"}, {"SMALLINT", "Short"}, {"TINYINT", "Byte"},
    {"BIGINT", "Long"}, {"MEDIUMINT", "Integer"},
    {"DECIMAL", "BigDecimal"}, {"NUMERIC", "BigDecimal"}, {"MONEY", "BigDecimal"},
    {"SMALLMONEY", "BigDecimal"}, {"FLOAT", "Float"}, {"REAL", "Float"}, {"DOUBLE", "Double"},
    {"DATE", "LocalDate"}, {"TIME", "LocalTime"}, {"TIMESTAMP", "LocalDateTime"},
    {"DATETIME", "LocalDateTime"}, {"DATETIME2", "LocalDateTime"}, {"SMALLDATETIME", "LocalDateTime"},
    {"BOOLEAN", "Boolean"}, {"BOOL", "Boolean"}, {"BIT", "Boolean"},
    {"BLOB", "byte[]"}, {"LONGBLOB", "byte[]"}, {"MEDIUMBLOB", "byte[]"}, {"TINYBLOB", "byte[]"},
    {"BINARY", "byte[]"}, {"VARBINARY", "byte[]"}, {"IMAGE", "byte[]"},
    {"JSON", "String"}, {"JSONB", "String"}, {"XML", "String"}, {"UUID", "UUID"}
};

// Mapeamento de imports necessários
static const std::map<std::string, std::string> IMPORT_MAP = {
    {"BigDecimal", "java.math.BigDecimal"}, {"BigInteger", "java.math.BigInteger"},
    {"LocalDate", "java.time.LocalDate"}, {"LocalTime", "java.time.LocalTime"},
    {"LocalDateTime", "java.time.LocalDateTime"}, {"UUID", "java.util.UUID"},
    {"Objects", "java.util.Objects"}
};

// Estruturas de dados
struct ColumnInfo {
    std::string name;
    std::string field_name;
    std::string sql_type;
    std::string java_type;
    bool nullable = true;
    bool primary_key = false;
    bool auto_increment = false;
    std::string default_value;
};

struct ForeignKeyInfo {
    std::string column_name;
    std::string referenced_table;
    std::string referenced_column;
    const struct TableInfo* referenced_table_info = nullptr;
};

struct TableInfo {
    std::string name;
    std::string class_name;
    std::vector<ColumnInfo> columns;
    std::vector<std::string> primary_keys;
    std::vector<ForeignKeyInfo> foreign_keys;
};

class SQLParserJPAGenerator {
public:
    void generateEntitiesFromSQL(const std::string& sql_file_path, 
                               const std::string& package_name = "com.example.entities", 
                               const std::string& output_dir = "./generated-entities") {
        // Criar diretório de saída
        fs::create_directories(output_dir);

        // Ler conteúdo SQL
        std::ifstream file(sql_file_path);
        if (!file.is_open()) {
            std::cerr << "Erro ao abrir arquivo SQL: " << sql_file_path << std::endl;
            return;
        }
        std::stringstream buffer;
        buffer << file.rdbuf();
        std::string sql_content = buffer.str();
        file.close();

        // Limpar e normalizar SQL
        sql_content = cleanSQL(sql_content);

        // Extrair informações das tabelas
        std::vector<TableInfo> tables = parseSQL(sql_content);

        // Processar relacionamentos
        processRelationships(tables, sql_content);

        // Gerar classes
        for (const auto& table : tables) {
            generateEntityClass(table, package_name, output_dir);
        }

        std::cout << "Geração concluída! " << tables.size() << " entidades criadas em: " << output_dir << std::endl;
    }

private:
    std::string cleanSQL(std::string sql_content) {
        // Remove comentários de linha
        sql_content = std::regex_replace(sql_content, std::regex("--.*$"), "", std::regex_constants::multiline);

        // Remove comentários de bloco
        sql_content = std::regex_replace(sql_content, std::regex("/\\*[\\s\\S]*?\\*/"), "");

        // Normalizar espaços
        sql_content = std::regex_replace(sql_content, std::regex("\\s+"), " ");

        return sql_content;
    }

    std::vector<TableInfo> parseSQL(const std::string& sql_content) {
        std::vector<TableInfo> tables;
        std::regex create_table_pattern(
            "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:`([^`]+)`|([\\w_]+))\\s*\\((.*?)\\)\\s*(?:ENGINE|DEFAULT|COMMENT|;|$)",
            std::regex::icase | std::regex::ECMAScript
        );

        std::sregex_iterator iter(sql_content.begin(), sql_content.end(), create_table_pattern);
        std::sregex_iterator end;

        for (; iter != end; ++iter) {
            std::string table_name = iter->str(1).empty() ? iter->str(2) : iter->str(1);
            std::string table_definition = iter->str(3);

            if (!table_name.empty()) {
                TableInfo table;
                table.name = table_name;
                table.class_name = toCamelCase(table_name, true);
                table.columns = parseColumns(table_definition);
                table.primary_keys = findPrimaryKeys(table_definition);
                table.foreign_keys = findForeignKeys(table_definition);

                // Marcar colunas como chave primária
                for (const auto& pk_column : table.primary_keys) {
                    for (auto& column : table.columns) {
                        if (column.name == pk_column) {
                            column.primary_key = true;
                        }
                    }
                }

                tables.push_back(table);
                std::cout << "Tabela encontrada: " << table.name << " (" << table.columns.size() << " colunas)" << std::endl;
            }
        }

        return tables;
    }

    std::vector<ColumnInfo> parseColumns(const std::string& table_definition) {
        std::vector<ColumnInfo> columns;
        std::vector<std::string> lines = splitByCommaIgnoringParentheses(table_definition);

        for (const auto& line : lines) {
            std::string trimmed = line;
            trimmed.erase(0, trimmed.find_first_not_of(" "));
            trimmed.erase(trimmed.find_last_not_of(" ") + 1);
            if (trimmed.empty() || isConstraintLine(trimmed)) {
                continue;
            }

            ColumnInfo column = parseColumn(trimmed);
            if (!column.name.empty()) {
                columns.push_back(column);
            }
        }

        return columns;
    }

    std::vector<std::string> splitByCommaIgnoringParentheses(const std::string& input) {
        std::vector<std::string> result;
        std::string current;
        int parentheses_count = 0;

        for (char c : input) {
            if (c == '(') {
                parentheses_count++;
            } else if (c == ')') {
                parentheses_count--;
            } else if (c == ',' && parentheses_count == 0) {
                result.push_back(trim(current));
                current.clear();
                continue;
            }
            current += c;
        }

        if (!current.empty()) {
            result.push_back(trim(current));
        }

        return result;
    }

    bool isConstraintLine(const std::string& line) {
        std::string upper_line = line;
        std::transform(upper_line.begin(), upper_line.end(), upper_line.begin(), ::toupper);
        return upper_line.find("PRIMARY KEY") == 0 ||
               upper_line.find("FOREIGN KEY") == 0 ||
               upper_line.find("KEY") == 0 ||
               upper_line.find("INDEX") == 0 ||
               upper_line.find("UNIQUE") == 0 ||
               upper_line.find("CONSTRAINT") == 0 ||
               upper_line.find("CHECK") == 0;
    }

    ColumnInfo parseColumn(const std::string& column_definition) {
        ColumnInfo column;
        std::regex column_pattern(
            "(?:`([^`]+)`|([\\w_]+))\\s+([\\w()\\s,]+?)\\s*(?:(NOT\\s+NULL|NULL))?\\s*(?:(AUTO_INCREMENT|IDENTITY))?\\s*(?:(PRIMARY\\s+KEY))?\\s*(?:DEFAULT\\s+([^,\\s]+))?\\s*(?:COMMENT\\s+['\"]([^'\"]*?)['\"])?",
            std::regex::icase
        );

        std::smatch match;
        if (std::regex_match(column_definition, match, column_pattern)) {
            column.name = match[1].str().empty() ? match[2].str() : match[1].str();
            column.field_name = toCamelCase(column.name, false);
            std::string sql_type = match[3].str();
            std::transform(sql_type.begin(), sql_type.end(), sql_type.begin(), ::toupper);
            column.sql_type = extractBaseType(sql_type);
            column.java_type = convertSQLTypeToJava(column.sql_type);

            if (sql_type.find("UNSIGNED") != std::string::npos) {
                column.java_type = adjustForUnsigned(column.java_type);
            }

            column.nullable = match[4].str().empty() || match[4].str() == "NULL";
            column.auto_increment = !match[5].str().empty();
            column.primary_key = !match[6].str().empty();
            column.default_value = match[7].str();
        } else {
            std::cerr << "Aviso: Não foi possível parsear a definição da coluna: " << column_definition << std::endl;
        }

        return column;
    }

    std::string extractBaseType(const std::string& sql_type) {
        std::string result = sql_type;
        size_t paren_index = result.find('(');
        if (paren_index != std::string::npos) {
            result = result.substr(0, paren_index);
        }
        result = std::regex_replace(result, std::regex("\\s+UNSIGNED"), "");
        return trim(result);
    }

    std::string adjustForUnsigned(const std::string& java_type) {
        if (java_type == "Byte") return "Short";
        if (java_type == "Short") return "Integer";
        if (java_type == "Integer") return "Long";
        if (java_type == "Long") return "BigInteger";
        return java_type;
    }

    std::vector<std::string> findPrimaryKeys(const std::string& table_definition) {
        std::vector<std::string> primary_keys;
        std::regex pk_pattern("PRIMARY\\s+KEY\\s*\\(([^)]+)\\)", std::regex::icase);
        std::smatch match;
        if (std::regex_search(table_definition, match, pk_pattern)) {
            std::string pk_columns = match[1].str();
            std::stringstream ss(pk_columns);
            std::string column;
            while (std::getline(ss, column, ',')) {
                column = trim(column);
                column = std::regex_replace(column, std::regex("`"), "");
                primary_keys.push_back(column);
            }
        }
        return primary_keys;
    }

    std::vector<ForeignKeyInfo> findForeignKeys(const std::string& table_definition) {
        std::vector<ForeignKeyInfo> foreign_keys;
        std::regex fk_pattern(
            "FOREIGN\\s+KEY\\s*\\(([^)]+)\\)\\s*REFERENCES\\s+(?:`([^`]+)`|([\\w_]+))\\s*\\(([^)]+)\\)",
            std::regex::icase
        );

        std::sregex_iterator iter(table_definition.begin(), table_definition.end(), fk_pattern);
        std::sregex_iterator end;

        for (; iter != end; ++iter) {
            ForeignKeyInfo fk;
            fk.column_name = std::regex_replace(iter->str(1), std::regex("`"), "");
            fk.referenced_table = iter->str(2).empty() ? iter->str(3) : iter->str(2);
            fk.referenced_column = std::regex_replace(iter->str(4), std::regex("`"), "");
            foreign_keys.push_back(fk);
        }

        return foreign_keys;
    }

    void processRelationships(std::vector<TableInfo>& tables, const std::string& sql_content) {
        std::map<std::string, TableInfo*> table_map;
        for (auto& table : tables) {
            table_map[table.name] = &table;
        }

        for (auto& table : tables) {
            for (auto& fk : table.foreign_keys) {
                if (table_map.find(fk.referenced_table) != table_map.end()) {
                    fk.referenced_table_info = table_map[fk.referenced_table];
                }
            }
        }
    }

    std::string convertSQLTypeToJava(const std::string& sql_type) {
        auto it = SQL_TO_JAVA_TYPE_MAP.find(sql_type);
        return it != SQL_TO_JAVA_TYPE_MAP.end() ? it->second : "String";
    }

    void generateEntityClass(const TableInfo& table, const std::string& package_name, const std::string& output_dir) {
        // Determinar imports necessários
        std::set<std::string> imports = {"javax.persistence.*", "java.io.Serializable"};
        for (const auto& column : table.columns) {
            auto it = IMPORT_MAP.find(column.java_type);
            if (it != IMPORT_MAP.end()) {
                imports.insert(it->second);
            }
        }

        std::stringstream sb;

        // Package
        sb << "package " << package_name << ";\n\n";

        // Imports
        for (const auto& imp : imports) {
            sb << "import " << imp << ";\n";
        }
        sb << "\n";

        // Documentação da classe
        sb << "/**\n";
        sb << " * Entidade JPA para a tabela " << table.name << "\n";
        sb << " * Gerada automaticamente pelo SQLParserJPAGenerator\n";
        sb << " */\n";

        // Anotações da classe
        sb << "@Entity\n";
        sb << "@Table(name = \"" << table.name << "\")\n";
        sb << "public class " << table.class_name << " implements Serializable {\n\n";
        sb << "    private static final long serialVersionUID = 1L;\n\n";

        // Campos
        for (const auto& column : table.columns) {
            generateField(sb, column);
        }

        // Relacionamentos
        for (const auto& fk : table.foreign_keys) {
            if (fk.referenced_table_info) {
                generateRelationshipField(sb, fk);
            }
        }

        // Construtores
        generateConstructors(sb, table);

        // Getters e Setters
        for (const auto& column : table.columns) {
            generateGetterSetter(sb, column);
        }

        for (const auto& fk : table.foreign_keys) {
            if (fk.referenced_table_info) {
                generateRelationshipGetterSetter(sb, fk);
            }
        }

        // equals e hashCode
        generateEqualsHashCode(sb, table);

        // toString
        generateToString(sb, table);

        sb << "}\n";

        // Escrever arquivo
        std::string file_name = table.class_name + ".java";
        fs::path output_path = fs::path(output_dir) / file_name;
        std::ofstream file(output_path);
        file << sb.str();
        file.close();

        std::cout << "Entidade gerada: " << file_name << std::endl;
    }

    void generateField(std::stringstream& sb, const ColumnInfo& column) {
        sb << "    /**\n";
        sb << "     * Campo " << column.name;
        if (!column.default_value.empty()) {
            sb << " (default: " << column.default_value << ")";
        }
        sb << "\n     */\n";

        if (column.primary_key) {
            sb << "    @Id\n";
            if (column.auto_increment) {
                sb << "    @GeneratedValue(strategy = GenerationType.IDENTITY)\n";
            }
        }

        sb << "    @Column(name = \"" << column.name << "\"";
        if (!column.nullable) {
            sb << ", nullable = false";
        }
        if (!column.default_value.empty() && !column.primary_key) {
            sb << ", columnDefinition = \"" << column.sql_type;
            if (!column.nullable) {
                sb << " NOT NULL";
            }
            sb << " DEFAULT " << column.default_value << "\"";
        }
        sb << ")\n";

        sb << "    private " << column.java_type << " " << column.field_name << ";\n\n";
    }

    void generateRelationshipField(std::stringstream& sb, const ForeignKeyInfo& fk) {
        std::string referenced_class_name = fk.referenced_table_info->class_name;
        std::string field_name = toCamelCase(fk.referenced_table, false);
        sb << "    /**\n";
        sb << "     * Relacionamento com " << fk.referenced_table << "\n";
        sb << "     */\n";
        sb << "    @ManyToOne(fetch = FetchType.LAZY)\n";
        sb << "    @JoinColumn(name = \"" << fk.column_name << "\")\n";
        sb << "    private " << referenced_class_name << " " << field_name << ";\n\n";
    }

    void generateConstructors(std::stringstream& sb, const TableInfo& table) {
        // Construtor vazio
        sb << "    /**\n";
        sb << "     * Construtor vazio\n";
        sb << "     */\n";
        sb << "    public " << table.class_name << "() {\n";
        sb << "    }\n\n";

        // Construtor com campos obrigatórios
        std::vector<ColumnInfo> required_columns;
        for (const auto& col : table.columns) {
            if (!col.nullable && !col.auto_increment) {
                required_columns.push_back(col);
            }
        }

        if (!required_columns.empty()) {
            sb << "    /**\n";
            sb << "     * Construtor com campos obrigatórios\n";
            sb << "     */\n";
            sb << "    public " << table.class_name << "(";
            for (size_t i = 0; i < required_columns.size(); ++i) {
                const auto& col = required_columns[i];
                sb << col.java_type << " " << col.field_name;
                if (i < required_columns.size() - 1) {
                    sb << ", ";
                }
            }
            sb << ") {\n";
            for (const auto& col : required_columns) {
                sb << "        this." << col.field_name << " = " << col.field_name << ";\n";
            }
            sb << "    }\n\n";
        }
    }

    void generateGetterSetter(std::stringstream& sb, const ColumnInfo& column) {
        std::string capitalized_field_name = capitalizeFirst(column.field_name);
        // Getter
        sb << "    public " << column.java_type << " get" << capitalized_field_name << "() {\n";
        sb << "        return " << column.field_name << ";\n";
        sb << "    }\n\n";
        // Setter
        sb << "    public void set" << capitalized_field_name << "(" << column.java_type << " " << column.field_name << ") {\n";
        sb << "        this." << column.field_name << " = " << column.field_name << ";\n";
        sb << "    }\n\n";
    }

    void generateRelationshipGetterSetter(std::stringstream& sb, const ForeignKeyInfo& fk) {
        std::string referenced_class_name = fk.referenced_table_info->class_name;
        std::string field_name = toCamelCase(fk.referenced_table, false);
        std::string capitalized_field_name = capitalizeFirst(field_name);
        // Getter
        sb << "    public " << referenced_class_name << " get" << capitalized_field_name << "() {\n";
        sb << "        return " << field_name << ";\n";
        sb << "    }\n\n";
        // Setter
        sb << "    public void set" << capitalized_field_name << "(" << referenced_class_name << " " << field_name << ") {\n";
        sb << "        this." << field_name << " = " << field_name << ";\n";
        sb << "    }\n\n";
    }

    void generateEqualsHashCode(std::stringstream& sb, const TableInfo& table) {
        std::vector<ColumnInfo> pk_columns;
        for (const auto& col : table.columns) {
            if (col.primary_key) {
                pk_columns.push_back(col);
            }
        }

        if (!pk_columns.empty()) {
            sb << "    @Override\n";
            sb << "    public boolean equals(Object o) {\n";
            sb << "        if (this == o) return true;\n";
            sb << "        if (o == null || getClass() != o.getClass()) return false;\n";
            sb << "        " << table.class_name << " that = (" << table.class_name << ") o;\n";
            sb << "        return ";
            for (size_t i = 0; i < pk_columns.size(); ++i) {
                const auto& col = pk_columns[i];
                sb << "Objects.equals(" << col.field_name << ", that." << col.field_name << ")";
                if (i < pk_columns.size() - 1) {
                    sb << " && ";
                }
            }
            sb << ";\n";
            sb << "    }\n\n";

            sb << "    @Override\n";
            sb << "    public int hashCode() {\n";
            sb << "        return Objects.hash(";
            for (size_t i = 0; i < pk_columns.size(); ++i) {
                const auto& col = pk_columns[i];
                sb << col.field_name;
                if (i < pk_columns.size() - 1) {
                    sb << ", ";
                }
            }
            sb << ");\n";
            sb << "    }\n\n";
        }
    }

    void generateToString(std::stringstream& sb, const TableInfo& table) {
        sb << "    @Override\n";
        sb << "    public String toString() {\n";
        sb << "        return \"" << table.class_name << "{\" +\n";
        for (size_t i = 0; i < table.columns.size(); ++i) {
            const auto& column = table.columns[i];
            sb << "                \"" << column.field_name << "=\" + " << column.field_name;
            if (i < table.columns.size() - 1) {
                sb << " + \", \" +\n";
            } else {
                sb << " +\n";
            }
        }
        sb << "                \"}\";\n";
        sb << "    }\n";
    }

    std::string toCamelCase(const std::string& input, bool capitalize_first) {
        if (input.empty()) return input;

        std::string result;
        bool capitalize_next = capitalize_first;

        for (char c : input) {
            if (c == '_' || c == '-') {
                capitalize_next = true;
            } else if (capitalize_next) {
                result += std::toupper(c);
                capitalize_next = false;
            } else {
                result += std::tolower(c);
            }
        }

        return result;
    }

    std::string capitalizeFirst(const std::string& input) {
        if (input.empty()) return input;
        std::string result = input;
        result[0] = std::toupper(result[0]);
        return result;
    }

    std::string trim(const std::string& str) {
        size_t first = str.find_first_not_of(" \t\n\r");
        size_t last = str.find_last_not_of(" \t\n\r");
        if (first == std::string::npos) return "";
        return str.substr(first, last - first + 1);
    }
};

int main(int argc, char* argv[]) {
    if (argc < 2) {
        std::cout << "Uso: " << argv[0] << " <caminho_ficheiro_sql> [pacote_destino] [diretorio_saida]\n";
        std::cout << "Exemplo: " << argv[0] << " schema.sql com.example.entities ./generated-classes\n";
        return 1;
    }

    std::string sql_file_path = argv[1];
    std::string package_name = argc > 2 ? argv[2] : "com.example.entities";
    std::string output_dir = argc > 3 ? argv[3] : "./generated-entities";

    try {
        SQLParserJPAGenerator generator;
        generator.generateEntitiesFromSQL(sql_file_path, package_name, output_dir);
    } catch (const std::exception& e) {
        std::cerr << "Erro ao gerar entidades: " << e.what() << std::endl;
        return 1;
    }

    return 0;
}