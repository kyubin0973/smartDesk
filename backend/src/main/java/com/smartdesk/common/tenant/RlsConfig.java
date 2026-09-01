package com.smartdesk.common.tenant;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 자동 구성된 DataSource(Hikari)를 RlsDataSource 로 감싼다 → JPA·JdbcTemplate 은 smartdesk_app 롤로 동작.
 * Flyway 는 spring.flyway.url 로 별도 커넥션(소유자)을 쓰므로 이 래핑의 영향을 받지 않는다.
 */
@Component
public class RlsConfig implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource ds && !(bean instanceof RlsDataSource)) {
            return new RlsDataSource(ds);
        }
        return bean;
    }
}
