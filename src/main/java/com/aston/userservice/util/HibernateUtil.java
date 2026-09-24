package com.aston.userservice.util;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public final class HibernateUtil {
    private HibernateUtil() { }

    public static SessionFactory buildSessionFactory() {
        return new Configuration().configure().buildSessionFactory();
    }
}
