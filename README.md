# Note Nexus Application Backend (Java Spring Boot)

Fuel your note-taking experience with a powerful and secure backend!

Notes Nexus App Backend (Java Spring Boot)
Fuel your note-taking experience with a powerful and secure backend!

This Spring Boot application provides the API backbone for the Notes App, enabling seamless note management through RESTful APIs.


## Features :

- CRUD Operations: Create, read, update, and delete notes with ease.
- Pagination: Efficiently navigate through large note sets with page-based access.
- Spring Security: Protect your notes with robust security features:
    - CSRF Protection: Prevent unauthorized requests and data manipulation.
    - JWT Authentication: Securely verify user identity after login using JSON Web Tokens.
    - Registration, Login, Reset Password, Change Password: Manage user accounts with intuitive functionalities.
- Database Integration: Connect to a database (e.g., MySQL, PostgreSQL) to persist your notes safely.


## Getting Started :

### Prerequisites : 

- Java 17 or above

- Maven

- Docker & Docker Compose

- IDE (e.g., IntelliJ IDEA, VSCode)

### Instructions : 

1. **Clone the repository :**

```
git clone https://github.com/PavanSingerReddy/note-nexus-backend
```

2. **Navigate to the project directory :**
```
cd note-nexus-backend\
```

3. **Install dependencies :**

```
mvn install
```

4. **Configure database connection :**
- Edit application-mysql.yml file with your database credentials if you are using mysql as a database server

- Or edit application-postgresql.yml file with your database credentials if you are using postgresql 

- now after adding the database credentials change the application.yml file and change spring>profiles>active property if you are using mysql then change the property name to mysql or if you are using postgresql then change the property name to postgresql.

- edit the host, username, password properties of the mail config properties in the application.yml file with the host property configured to the smtp server address of the mail provider like `smtp-mail.outlook.com` for outlook and `smtp.gmail.com` for gmail and username property with the username and password property with the app password of the email.This email will be used to send the confirmation email for verifying the user account

5. **Start Infrastructure Services (Redis) :**

    **Start Redis (with secure ACL configured) :**
    ```bash
    docker-compose -f docker-compose-redis.yml up -d
    ```
    To stop Redis:
    ```bash
    docker-compose -f docker-compose-redis.yml down
    ```

6. **Start the server :**

    **IntelliJ IDEA :**
    - Open the project in IntelliJ IDEA.
    - Run the main class in `com.pavansingerreddy.note.NotesTakingBackendApplication.java`.

    **VSCode :**
    - Open a terminal in VSCode within the project directory.
    - Run `mvn spring-boot:run`.


7. **Start the frontend of the application :**

    After starting the backend server, you can start the frontend React application. The frontend application repository is : https://github.com/PavanSingerReddy/note-nexus-frontend

    You can access the whole application live at https://pavansingerreddy.tech/.

## Contributing:
We welcome contributions! Feel free to open issues or pull requests to improve the backend functionality.

## License:
MIT License: LICENSE