package com.reconcilex.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

/**
 * Test-scoped JDBC statement counter using dynamic proxies.
 * Intercepts prepareStatement and createStatement calls to assert
 * precise SQL query counts (detecting N+1 queries).
 */
public class TestStatementCounter {

    private final AtomicInteger statementCount = new AtomicInteger(0);
    private final List<String> executedStatements = Collections.synchronizedList(new ArrayList<>());

    public void reset() {
        statementCount.set(0);
        executedStatements.clear();
    }

    public int getCount() {
        return statementCount.get();
    }

    public List<String> getStatements() {
        return new ArrayList<>(executedStatements);
    }

    public void record(String sql) {
        statementCount.incrementAndGet();
        if (sql != null) {
            executedStatements.add(sql);
        }
    }

    /**
     * Returns a DataSource proxy that counts all statements executed across
     * all connections obtained from it.
     */
    public DataSource wrap(DataSource target) {
        return (DataSource) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                new Class<?>[]{DataSource.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        Object result = method.invoke(target, args);
                        if ("getConnection".equals(method.getName()) && result instanceof Connection conn) {
                            return wrapConnection(conn);
                        }
                        return result;
                    }
                }
        );
    }

    private Connection wrapConnection(Connection targetConn) {
        return (Connection) Proxy.newProxyInstance(
                targetConn.getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String methodName = method.getName();
                        if ("prepareStatement".equals(methodName) || "prepareCall".equals(methodName)) {
                            String sql = args != null && args.length > 0 && args[0] instanceof String
                                    ? (String) args[0] : null;
                            record(sql);
                        } else if ("createStatement".equals(methodName)) {
                            record("createStatement");
                        }
                        return method.invoke(targetConn, args);
                    }
                }
        );
    }
}
