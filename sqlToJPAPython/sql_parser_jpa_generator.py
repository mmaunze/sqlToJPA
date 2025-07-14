import os
import re
import uuid
from pathlib import Path
from typing import List, Dict, Optional
from dataclasses import dataclass

# Mapeamento de tipos SQL para Java
SQL_TO_JAVA_TYPE_MAP = {
    'VARCHAR': 'String',
    'CHAR': 'String',
    'TEXT': 'String',
    'LONGTEXT': 'String',
    'MEDIUMTEXT': 'String',
    'TINYTEXT': 'String',
    'CLOB': 'String',
    'NVARCHAR': 'String',
    'NCHAR': 'String',
    'NTEXT': 'String',
    
    'INT': 'Integer',
    'INTEGER': 'Integer',
    'SMALLINT': 'Short',
    'TINYINT': 'Byte',
    'BIGINT': 'Long',
    'MEDIUMINT': 'Integer',
    
    'DECIMAL': 'BigDecimal',
    'NUMERIC': 'BigDecimal',
    'MONEY': 'BigDecimal',
    'SMALLMONEY': 'BigDecimal',
    'FLOAT': 'Float',
    'REAL': 'Float',
    'DOUBLE': 'Double',
    
    'DATE': 'LocalDate',
    'TIME': 'LocalTime',
    'TIMESTAMP': 'LocalDateTime',
    'DATETIME': 'LocalDateTime',
    'DATETIME2': 'LocalDateTime',
    'SMALLDATETIME': 'LocalDateTime',
    
    'BOOLEAN': 'Boolean',
    'BOOL': 'Boolean',
    'BIT': 'Boolean',
    
    'BLOB': 'byte[]',
    'LONGBLOB': 'byte[]',
    'MEDIUMBLOB': 'byte[]',
    'TINYBLOB': 'byte[]',
    'BINARY': 'byte[]',
    'VARBINARY': 'byte[]',
    'IMAGE': 'byte[]',
    
    'JSON': 'String',
    'JSONB': 'String',
    'XML': 'String',
    'UUID': 'UUID'
}

# Mapeamento de imports necessários
IMPORT_MAP = {
    'BigDecimal': 'java.math.BigDecimal',
    'BigInteger': 'java.math.BigInteger',
    'LocalDate': 'java.time.LocalDate',
    'LocalTime': 'java.time.LocalTime',
    'LocalDateTime': 'java.time.LocalDateTime',
    'UUID': 'java.util.UUID',
    'Objects': 'java.util.Objects'
}

@dataclass
class ColumnInfo:
    name: str
    field_name: str
    sql_type: str
    java_type: str
    nullable: bool = True
    primary_key: bool = False
    auto_increment: bool = False
    default_value: Optional[str] = None

@dataclass
class ForeignKeyInfo:
    column_name: str
    referenced_table: str
    referenced_column: str
    referenced_table_info: Optional['TableInfo'] = None

@dataclass
class TableInfo:
    name: str
    class_name: str
    columns: List[ColumnInfo]
    primary_keys: List[str]
    foreign_keys: List[ForeignKeyInfo]

class SQLParserJPAGenerator:
    def generate_entities_from_sql(self, sql_file_path: str, package_name: str = 'com.example.entities', output_dir: str = './generated-entities') -> None:
        # Criar diretório de saída
        os.makedirs(output_dir, exist_ok=True)
        
        # Ler conteúdo SQL
        sql_content = Path(sql_file_path).read_text()
        
        # Limpar e normalizar SQL
        sql_content = self._clean_sql(sql_content)
        
        # Extrair informações das tabelas
        tables = self._parse_sql(sql_content)
        
        # Processar relacionamentos
        self._process_relationships(tables, sql_content)
        
        # Gerar classes
        for table in tables:
            self._generate_entity_class(table, package_name, output_dir)
        
        print(f"Geração concluída! {len(tables)} entidades criadas em: {output_dir}")

    def _clean_sql(self, sql_content: str) -> str:
        # Remove comentários de linha
        sql_content = re.sub(r'--.*$', '', sql_content, flags=re.MULTILINE)
        
        # Remove comentários de bloco
        sql_content = re.sub(r'/\*[\s\S]*?\*/', '', sql_content)
        
        # Normalizar espaços
        sql_content = re.sub(r'\s+', ' ', sql_content)
        
        return sql_content.strip()

    def _parse_sql(self, sql_content: str) -> List[TableInfo]:
        tables = []
        
        # Regex para CREATE TABLE
        create_table_pattern = re.compile(
            r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:`([^`]+)`|([\w_]+))\s*\((.*?)\)\s*(?:ENGINE|DEFAULT|COMMENT|;|$)',
            re.IGNORECASE | re.DOTALL
        )
        
        for match in create_table_pattern.finditer(sql_content):
            table_name = match.group(1) or match.group(2)
            table_definition = match.group(3)
            
            if table_name and table_name.strip():
                table = TableInfo(
                    name=table_name.strip(),
                    class_name=self._to_camel_case(table_name, capitalize_first=True),
                    columns=self._parse_columns(table_definition),
                    primary_keys=self._find_primary_keys(table_definition),
                    foreign_keys=self._find_foreign_keys(table_definition)
                )
                
                # Marcar colunas como chave primária
                for pk_column in table.primary_keys:
                    for column in table.columns:
                        if column.name == pk_column:
                            column.primary_key = True
                
                tables.append(table)
                print(f"Tabela encontrada: {table.name} ({len(table.columns)} colunas)")
        
        return tables

    def _parse_columns(self, table_definition: str) -> List[ColumnInfo]:
        columns = []
        lines = self._split_by_comma_ignoring_parentheses(table_definition)
        
        for line in lines:
            line = line.strip()
            if not line or self._is_constraint_line(line):
                continue
            
            column = self._parse_column(line)
            if column:
                columns.append(column)
        
        return columns

    def _split_by_comma_ignoring_parentheses(self, input_str: str) -> List[str]:
        result = []
        current = []
        parentheses_count = 0
        
        for char in input_str:
            if char == '(':
                parentheses_count += 1
            elif char == ')':
                parentheses_count -= 1
            elif char == ',' and parentheses_count == 0:
                result.append(''.join(current).strip())
                current = []
                continue
            current.append(char)
        
        if current:
            result.append(''.join(current).strip())
        
        return result

    def _is_constraint_line(self, line: str) -> bool:
        upper_line = line.upper()
        return any(upper_line.startswith(prefix) for prefix in [
            'PRIMARY KEY', 'FOREIGN KEY', 'KEY', 'INDEX', 'UNIQUE', 'CONSTRAINT', 'CHECK'
        ])

    def _parse_column(self, column_definition: str) -> Optional[ColumnInfo]:
        column_pattern = re.compile(
            r'(?:`([^`]+)`|([\w_]+))\s+([\w()\s,]+?)\s*(?:(NOT\s+NULL|NULL))?\s*(?:(AUTO_INCREMENT|IDENTITY))?\s*(?:(PRIMARY\s+KEY))?\s*(?:DEFAULT\s+([^,\s]+))?\s*(?:COMMENT\s+[\'"]([^\'"]*)[\'"])?',
            re.IGNORECASE
        )
        
        match = column_pattern.match(column_definition.strip())
        if match:
            name = match.group(1) or match.group(2)
            sql_type = match.group(3).strip().upper()
            base_type = self._extract_base_type(sql_type)
            java_type = self._convert_sql_type_to_java(base_type)
            
            # Verificar se é unsigned
            if 'UNSIGNED' in sql_type:
                java_type = self._adjust_for_unsigned(java_type)
            
            return ColumnInfo(
                name=name,
                field_name=self._to_camel_case(name, capitalize_first=False),
                sql_type=base_type,
                java_type=java_type,
                nullable=match.group(4) is None or match.group(4).upper() == 'NULL',
                auto_increment=bool(match.group(5)),
                primary_key=bool(match.group(6)),
                default_value=match.group(7)
            )
        else:
            print(f"Aviso: Não foi possível parsear a definição da coluna: {column_definition}")
            return None

    def _extract_base_type(self, sql_type: str) -> str:
        paren_index = sql_type.find('(')
        if paren_index != -1:
            sql_type = sql_type[:paren_index]
        return re.sub(r'\s+UNSIGNED', '', sql_type).strip()

    def _adjust_for_unsigned(self, java_type: str) -> str:
        unsigned_map = {
            'Byte': 'Short',
            'Short': 'Integer',
            'Integer': 'Long',
            'Long': 'BigInteger'
        }
        return unsigned_map.get(java_type, java_type)

    def _find_primary_keys(self, table_definition: str) -> List[str]:
        primary_keys = []
        pk_pattern = re.compile(r'PRIMARY\s+KEY\s*\(([^)]+)\)', re.IGNORECASE)
        match = pk_pattern.search(table_definition)
        if match:
            pk_columns = match.group(1)
            primary_keys = [col.strip().replace('`', '') for col in pk_columns.split(',')]
        return primary_keys

    def _find_foreign_keys(self, table_definition: str) -> List[ForeignKeyInfo]:
        foreign_keys = []
        fk_pattern = re.compile(
            r'FOREIGN\s+KEY\s*\(([^)]+)\)\s*REFERENCES\s+(?:`([^`]+)`|([\w_]+))\s*\(([^)]+)\)',
            re.IGNORECASE
        )
        
        for match in fk_pattern.finditer(table_definition):
            foreign_keys.append(ForeignKeyInfo(
                column_name=match.group(1).strip().replace('`', ''),
                referenced_table=match.group(2) or match.group(3),
                referenced_column=match.group(4).strip().replace('`', '')
            ))
        return foreign_keys

    def _process_relationships(self, tables: List[TableInfo], sql_content: str) -> None:
        table_map = {table.name: table for table in tables}
        for table in tables:
            for fk in table.foreign_keys:
                if fk.referenced_table in table_map:
                    fk.referenced_table_info = table_map[fk.referenced_table]

    def _convert_sql_type_to_java(self, sql_type: str) -> str:
        return SQL_TO_JAVA_TYPE_MAP.get(sql_type, 'String')

    def _generate_entity_class(self, table: TableInfo, package_name: str, output_dir: str) -> None:
        # Determinar imports necessários
        imports = {'javax.persistence.*', 'java.io.Serializable'}
        for column in table.columns:
            if column.java_type in IMPORT_MAP:
                imports.add(IMPORT_MAP[column.java_type])
        
        # Construir conteúdo da classe
        lines = []
        lines.append(f"package {package_name};")
        lines.append("")
        
        # Imports
        for imp in sorted(imports):
            lines.append(f"import {imp};")
        
        lines.append("")
        lines.append("/**")
        lines.append(f" * Entidade JPA para a tabela {table.name}")
        lines.append(" * Gerada automaticamente pelo SQLParserJPAGenerator")
        lines.append(" */")
        lines.append("@Entity")
        lines.append(f"@Table(name = \"{table.name}\")")
        lines.append(f"public class {table.class_name} implements Serializable {{")
        lines.append("")
        lines.append("    private static final long serialVersionUID = 1L;")
        lines.append("")
        
        # Campos
        for column in table.columns:
            self._generate_field(lines, column)
        
        # Relacionamentos
        for fk in table.foreign_keys:
            if fk.referenced_table_info:
                self._generate_relationship_field(lines, fk)
        
        # Construtores
        self._generate_constructors(lines, table)
        
        # Getters e Setters
        for column in table.columns:
            self._generate_getter_setter(lines, column)
        
        for fk in table.foreign_keys:
            if fk.referenced_table_info:
                self._generate_relationship_getter_setter(lines, fk)
        
        # equals e hashCode
        self._generate_equals_hash_code(lines, table)
        
        # toString
        self._generate_to_string(lines, table)
        
        lines.append("}")
        
        # Escrever arquivo
        file_name = f"{table.class_name}.java"
        output_path = os.path.join(output_dir, file_name)
        with open(output_path, 'w') as f:
            f.write('\n'.join(lines))
        
        print(f"Entidade gerada: {file_name}")

    def _generate_field(self, lines: List[str], column: ColumnInfo) -> None:
        lines.append(f"    /**")
        lines.append(f"     * Campo {column.name}" + (f" (default: {column.default_value})" if column.default_value else ""))
        lines.append(f"     */")
        
        if column.primary_key:
            lines.append("    @Id")
            if column.auto_increment:
                lines.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)")
        
        column_def = [f"    @Column(name = \"{column.name}\""]
        if not column.nullable:
            column_def.append(", nullable = false")
        if column.default_value and not column.primary_key:
            column_def.append(f", columnDefinition = \"{column.sql_type}")
            if not column.nullable:
                column_def.append(" NOT NULL")
            column_def.append(f" DEFAULT {column.default_value}\"")
        column_def.append(")")
        
        lines.append(''.join(column_def))
        lines.append(f"    private {column.java_type} {column.field_name};")
        lines.append("")

    def _generate_relationship_field(self, lines: List[str], fk: ForeignKeyInfo) -> None:
        referenced_class_name = fk.referenced_table_info.class_name
        field_name = self._to_camel_case(fk.referenced_table, capitalize_first=False)
        lines.append(f"    /**")
        lines.append(f"     * Relacionamento com {fk.referenced_table}")
        lines.append(f"     */")
        lines.append(f"    @ManyToOne(fetch = FetchType.LAZY)")
        lines.append(f"    @JoinColumn(name = \"{fk.column_name}\")")
        lines.append(f"    private {referenced_class_name} {field_name};")
        lines.append("")

    def _generate_constructors(self, lines: List[str], table: TableInfo) -> None:
        # Construtor vazio
        lines.append(f"    /**")
        lines.append(f"     * Construtor vazio")
        lines.append(f"     */")
        lines.append(f"    public {table.class_name}() {{")
        lines.append(f"    }}")
        lines.append("")
        
        # Construtor com campos obrigatórios
        required_columns = [col for col in table.columns if not col.nullable and not col.auto_increment]
        if required_columns:
            lines.append(f"    /**")
            lines.append(f"     * Construtor com campos obrigatórios")
            lines.append(f"     */")
            lines.append(f"    public {table.class_name}(" + ", ".join(f"{col.java_type} {col.field_name}" for col in required_columns) + ") {")
            for col in required_columns:
                lines.append(f"        this.{col.field_name} = {col.field_name};")
            lines.append(f"    }}")
            lines.append("")

    def _generate_getter_setter(self, lines: List[str], column: ColumnInfo) -> None:
        capitalized_field_name = self._capitalize_first(column.field_name)
        # Getter
        lines.append(f"    public {column.java_type} get{capitalized_field_name}() {{")
        lines.append(f"        return {column.field_name};")
        lines.append(f"    }}")
        lines.append("")
        # Setter
        lines.append(f"    public void set{capitalized_field_name}({column.java_type} {column.field_name}) {{")
        lines.append(f"        this.{column.field_name} = {column.field_name};")
        lines.append(f"    }}")
        lines.append("")

    def _generate_relationship_getter_setter(self, lines: List[str], fk: ForeignKeyInfo) -> None:
        referenced_class_name = fk.referenced_table_info.class_name
        field_name = self._to_camel_case(fk.referenced_table, capitalize_first=False)
        capitalized_field_name = self._capitalize_first(field_name)
        # Getter
        lines.append(f"    public {referenced_class_name} get{capitalized_field_name}() {{")
        lines.append(f"        return {field_name};")
        lines.append(f"    }}")
        lines.append("")
        # Setter
        lines.append(f"    public void set{capitalized_field_name}({referenced_class_name} {field_name}) {{")
        lines.append(f"        this.{field_name} = {field_name};")
        lines.append(f"    }}")
        lines.append("")

    def _generate_equals_hash_code(self, lines: List[str], table: TableInfo) -> None:
        pk_columns = [col for col in table.columns if col.primary_key]
        if pk_columns:
            lines.append(f"    @Override")
            lines.append(f"    public boolean equals(Object o) {{")
            lines.append(f"        if (this == o) return true;")
            lines.append(f"        if (o == null || getClass() != o.getClass()) return false;")
            lines.append(f"        {table.class_name} that = ({table.class_name}) o;")
            lines.append(f"        return " + " && ".join(f"Objects.equals({col.field_name}, that.{col.field_name})" for col in pk_columns) + ";")
            lines.append(f"    }}")
            lines.append("")
            lines.append(f"    @Override")
            lines.append(f"    public int hashCode() {{")
            lines.append(f"        return Objects.hash(" + ", ".join(col.field_name for col in pk_columns) + ");")
            lines.append(f"    }}")
            lines.append("")

    def _generate_to_string(self, lines: List[str], table: TableInfo) -> None:
        lines.append(f"    @Override")
        lines.append(f"    public String toString() {{")
        lines.append(f"        return \"{table.class_name}{{\" +")
        for i, column in enumerate(table.columns):
            lines.append(f"                \"{column.field_name}=\" + {column.field_name}" + (" + \", \" +" if i < len(table.columns) - 1 else " +"))
        lines.append(f"                \"}}\";")
        lines.append(f"    }}")

    def _to_camel_case(self, input_str: str, capitalize_first: bool = False) -> str:
        if not input_str:
            return input_str
        
        result = []
        capitalize_next = capitalize_first
        
        for char in input_str:
            if char in '_-':
                capitalize_next = True
            elif capitalize_next:
                result.append(char.upper())
                capitalize_next = False
            else:
                result.append(char.lower())
        
        return ''.join(result)

    def _capitalize_first(self, input_str: str) -> str:
        if not input_str:
            return input_str
        return input_str[0].upper() + input_str[1:]

def main():
    import sys
    if len(sys.argv) < 2:
        print("Uso: python sql_parser_jpa_generator.py <caminho_ficheiro_sql> [pacote_destino] [diretorio_saida]")
        print("Exemplo: python sql_parser_jpa_generator.py schema.sql com.example.entities ./src/main/java")
        return
    
    sql_file_path = sys.argv[1]
    package_name = sys.argv[2] if len(sys.argv) > 2 else 'com.example.entities'
    output_dir = sys.argv[3] if len(sys.argv) > 3 else './generated-entities'
    
    try:
        generator = SQLParserJPAGenerator()
        generator.generate_entities_from_sql(sql_file_path, package_name, output_dir)
    except Exception as e:
        print(f"Erro ao gerar entidades: {str(e)}")
        import traceback
        traceback.print_exc()

if __name__ == '__main__':
    main()