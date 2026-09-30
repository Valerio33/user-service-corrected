package com.aston.userservice.util;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public final class HibernateUtil {
    private HibernateUtil() { }

    public static SessionFactory buildSessionFactory() {
        // Фабрикой владеет Main: он создаёт её один раз и закрывает при выходе.
        return new Configuration().configure().buildSessionFactory();
    }
}
