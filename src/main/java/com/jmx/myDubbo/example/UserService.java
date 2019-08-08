package com.jmx.myDubbo.example;

public interface UserService {
    UserServiceImpl.User getUser(Integer id);

    boolean delete(Integer id,UserServiceImpl.User user);
}
