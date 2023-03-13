package com.mile.springbatchtoy.jpa;

import com.mile.springbatchtoy.part3.PersonRepository;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PersonRepositoryTest {

    @Autowired
    private PersonRepository personRepository;

    @Test
    void delete_테스트() {
        personRepository.deleteAllInBatch();
    }
}