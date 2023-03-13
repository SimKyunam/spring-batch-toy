package com.mile.springbatchtoy.part3;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {

    void deleteAllBatch();
}
