package cn.study.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Types;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

/** All SQL remains parameterized; JSON and timestamps cross the API boundary consistently. */
@Repository
public class Db {
    public final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public Db(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }
    public List<Map<String,Object>> rows(String sql, Object... args) {
        return jdbc.query(sql, (rs, n) -> {
            var row = new LinkedHashMap<String,Object>();
            var meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                String name = meta.getColumnLabel(i);
                if (name.endsWith("_json")) name = name.substring(0, name.length()-5);
                Object value = rs.getObject(i);
                if (value != null && (meta.getColumnTypeName(i).equals("jsonb") || meta.getColumnTypeName(i).equals("json"))) {
                    try { value = mapper.readValue(rs.getString(i), Object.class); }
                    catch (Exception e) { throw new IllegalStateException("Stored JSON is invalid"); }
                } else if (value instanceof UUID) value = value.toString();
                else if (value instanceof java.sql.Timestamp stamp) value = stamp.toInstant().toString();
                else if (value instanceof java.sql.Date date) value = date.toLocalDate().toString();
                row.put(camel(name), value);
            }
            return row;
        }, args);
    }
    public Map<String,Object> one(String sql, Object... args) {
        var rows = rows(sql, args);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在");
        return rows.getFirst();
    }
    public long count(String sql, Object... args) {
        Long result = jdbc.queryForObject(sql, Long.class, args);
        return result == null ? 0 : result;
    }
    public int update(String sql, Object... args) { return jdbc.update(sql, args); }
    public String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("JSON serialization failed"); }
    }
    public static UUID uuid(Object value) { return UUID.fromString(value.toString()); }
    private static String camel(String input) {
        var result = new StringBuilder(); boolean upper = false;
        for (char c: input.toCharArray()) {
            if (c == '_') upper = true;
            else { result.append(upper ? Character.toUpperCase(c) : c); upper = false; }
        }
        return result.toString();
    }
}
