package eu.aston.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class QuerySql {

    private final List<Column> columns = new ArrayList<>();
    private final String table;

    public QuerySql(String table) {
        this.table = table;
    }

    public QuerySql addColumnGroup(String name, String sql) {
        columns.add(new Column(name, sql, ColumnType.GROUP));
        return this;
    }

    public QuerySql addColumnAgg(String name, String sql) {
        columns.add(new Column(name, sql, ColumnType.AGG));
        return this;
    }
    public QuerySql addColumnUnique(String name, String sql) {
        columns.add(new Column(name, sql, ColumnType.UNIQUE));
        return this;
    }

    public String createSqlQuery(String select, List<String> where) {
        Map<String, Column> columnsDict = columns.stream().collect(Collectors.toMap(Column::name, Function.identity()));
        List<String> selectColumns = new ArrayList<>();
        List<String> groupBy = new ArrayList<>();
        List<String> aggColumns = new ArrayList<>();
        List<String> uniqueColumns = new ArrayList<>();

        if (select != null && !select.isEmpty()) {
            String[] selectItems = select.split(",");
            for (String columnName : selectItems) {
                Column column = columnsDict.get(columnName.trim());
                if (column != null) {
                    if (ColumnType.GROUP==column.type()) {
                        selectColumns.add(column.sql());
                        groupBy.add(columnName);
                    } else if (ColumnType.AGG==column.type()) {
                        selectColumns.add(column.sql());
                        aggColumns.add(columnName);
                    } else if (ColumnType.UNIQUE==column.type()) {
                        selectColumns.add(column.sql());
                        uniqueColumns.add(columnName);
                    }
                } else {
                    throw new IllegalArgumentException("Invalid column name: " + columnName);
                }
            }

            if (selectColumns.isEmpty()) {
                throw new IllegalArgumentException("Select columns cannot be empty");
            }
        } else {
            selectColumns.add("*");
        }

        if (!aggColumns.isEmpty() && !uniqueColumns.isEmpty()) {
            throw new IllegalArgumentException("Agg and unique columns cannot be used together");
        }

        String whereSql = where.isEmpty() ? "1=1" : String.join(" AND ", where);

        if (!groupBy.isEmpty()) {
            return String.format("SELECT %s FROM %s WHERE %s GROUP BY %s",
                                 String.join(", ", selectColumns), table, whereSql, String.join(", ", groupBy));
        } else {
            return String.format("SELECT %s FROM %s WHERE %s",
                                 String.join(", ", selectColumns), table, whereSql);
        }
    }

    public enum ColumnType {
        GROUP, AGG, UNIQUE
    }

    public record Column(String name, String sql, ColumnType type) {

    }
}
