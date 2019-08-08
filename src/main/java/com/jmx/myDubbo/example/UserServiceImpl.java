package com.jmx.myDubbo.example;

import java.io.Serializable;

public class UserServiceImpl implements UserService{

    public User getUser(Integer id) {
        return new User(1,"小明","1990-12-11");
    }

    public boolean delete(Integer id, User user) {
        System.out.println("删除"+user.toString());
        return true;
    }

    public static class User implements Serializable{
        private Integer id;
        private String name;
        private String birthday;

        public User(Integer id, String name, String birthday) {
            this.id = id;
            this.name = name;
            this.birthday = birthday;
        }

        public User() {
        }

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getBirthday() {
            return birthday;
        }

        public void setBirthday(String birthday) {
            this.birthday = birthday;
        }
    }
}
