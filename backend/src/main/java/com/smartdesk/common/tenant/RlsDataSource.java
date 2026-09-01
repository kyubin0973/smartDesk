package com.smartdesk.common.tenant;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 단계 4: 애플리케이션 커넥션을 비특권 롤(smartdesk_app)로 전환하고, 문(statement) 생성 직전마다
 * 현재 {@link TenantContext} 로 Postgres 세션 변수를 맞춘다. 컨텍스트가 안 바뀌면 재설정하지 않으므로
 * 운영(요청당 컨텍스트 고정)에서는 커넥션당 1회, 테스트(한 트랜잭션에서 컨텍스트 전환)에서도 정확.
 */
public class RlsDataSource extends DelegatingDataSource {

    public RlsDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrap(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(super.getConnection(username, password));
    }

    private Connection wrap(Connection real) throws SQLException {
        try (Statement s = real.createStatement()) {
            s.execute("SET ROLE smartdesk_app");
        } catch (SQLException e) {
            real.close();
            throw e;
        }
        Handler h = new Handler(real);
        return (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{Connection.class}, h);
    }

    private static final class Handler implements InvocationHandler {
        private final Connection delegate;
        private TenantContext.Ctx applied;

        Handler(Connection delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (name.equals("createStatement") || name.equals("prepareStatement") || name.equals("prepareCall")) {
                syncContext();
            }
            try {
                return method.invoke(delegate, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private void syncContext() throws SQLException {
            TenantContext.Ctx ctx = TenantContext.current();
            if (ctx.equals(applied)) return;
            try (Statement s = delegate.createStatement()) {
                s.execute("SET app.client_id = '" + ctx.clientId() + "'");
                s.execute("SET app.is_si = '" + (ctx.si() ? "true" : "false") + "'");
            }
            applied = ctx;
        }
    }
}
