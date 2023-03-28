package com.mile.springbatchtoy.part4;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

public interface UserRepository extends JpaRepository<Users, Long> {
    Collection<Users> findAllByUpdateDate(LocalDate updateDate);
}
