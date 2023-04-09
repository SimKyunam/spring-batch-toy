package com.mile.springbatchtoy.part4;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

public interface UserRepository extends JpaRepository<Users, Long> {
    Collection<Users> findAllByUpdateDate(LocalDate updateDate);

    @Query(value = "select min(u.id) from Users u")
    long findMinId();

    @Query(value = "select max(u.id) from Users u")
    long findMaxId();
}
