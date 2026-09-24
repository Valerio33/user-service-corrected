package com.aston.userservice;

import com.aston.userservice.dao.UserDao;
import com.aston.userservice.dao.UserDaoImpl;
import com.aston.userservice.entity.User;
import com.aston.userservice.exception.DaoException;
import com.aston.userservice.util.HibernateUtil;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try (SessionFactory sessionFactory = HibernateUtil.buildSessionFactory();
             Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            UserDao userDao = new UserDaoImpl(sessionFactory);
            runMenu(userDao, scanner);
        } catch (RuntimeException error) {
            logger.error("Ошибка запуска или завершения приложения", error);
            System.err.println("Проверьте доступность PostgreSQL, наличие базы user_db "
                    + "и настройки hibernate.cfg.xml. Подробности — в журнале выше.");
            System.exit(1);
        }
    }

    private static void runMenu(UserDao userDao, Scanner scanner) {
        System.out.println("Добро пожаловать в User Service!");
        while (true) {
            System.out.println("\n1 - Создать пользователя");
            System.out.println("2 - Найти пользователя по ID");
            System.out.println("3 - Вывести всех пользователей");
            System.out.println("4 - Обновить данные пользователя");
            System.out.println("5 - Удалить пользователя");
            System.out.println("0 - Выход");
            try {
                switch (readLine(scanner, "Ваш выбор: ")) {
                    case "1" -> createUser(userDao, scanner);
                    case "2" -> {
                        User user = userDao.getById(readId(scanner));
                        System.out.println(user == null ? "Пользователь не найден." : "Найден: " + user);
                    }
                    case "3" -> {
                        List<User> users = userDao.getAll();
                        if (users.isEmpty()) { System.out.println("Список пользователей пуст."); }
                        else { users.forEach(System.out::println); }
                    }
                    case "4" -> updateUser(userDao, scanner);
                    case "5" -> System.out.println(userDao.delete(readId(scanner))
                            ? "Пользователь удалён." : "Пользователь не найден.");
                    case "0" -> { System.out.println("Завершение работы..."); return; }
                    default -> System.out.println("Неизвестная команда. Введите число от 0 до 5.");
                }
            } catch (DaoException error) {
                System.out.println("Ошибка: " + error.getMessage());
            } catch (IllegalArgumentException error) {
                System.out.println("Некорректный ввод: " + error.getMessage());
            } catch (NoSuchElementException endOfInput) {
                System.out.println("\nВвод завершён. Приложение закрывается.");
                return;
            }
        }
    }

    private static void createUser(UserDao userDao, Scanner scanner) {
        String name = readLine(scanner, "Введите имя: ");
        String email = readLine(scanner, "Введите email: ");
        int age = parseAge(readLine(scanner, "Введите возраст: "));
        User user = new User(name, email, age);
        userDao.save(user);
        System.out.println("Пользователь создан: " + user);
    }

    private static void updateUser(UserDao userDao, Scanner scanner) {
        User user = userDao.getById(readId(scanner));
        if (user == null) {
            System.out.println("Пользователь не найден.");
            return;
        }
        String name = readLine(scanner, "Новое имя (Enter — оставить '" + user.getName() + "'): ");
        String email = readLine(scanner, "Новый email (Enter — оставить '" + user.getEmail() + "'): ");
        String age = readLine(scanner, "Новый возраст (Enter — оставить " + user.getAge() + "): ");

        User values = new User(
                name.isEmpty() ? user.getName() : name,
                email.isEmpty() ? user.getEmail() : email,
                age.isEmpty() ? user.getAge() : parseAge(age));
        user.setName(values.getName());
        user.setEmail(values.getEmail());
        user.setAge(values.getAge());
        System.out.println(userDao.update(user)
                ? "Данные пользователя обновлены." : "Пользователь уже удалён.");
    }

    private static long readId(Scanner scanner) {
        try {
            long id = Long.parseLong(readLine(scanner, "Введите ID пользователя: "));
            if (id > 0) { return id; }
        } catch (NumberFormatException ignored) {
            // Сообщение для некорректного и неположительного ID.
        }
        throw new IllegalArgumentException("ID должен быть положительным целым числом.");
    }

    private static int parseAge(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Возраст должен быть целым числом.");
        }
    }

    private static String readLine(Scanner scanner, String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().strip();
    }
}
