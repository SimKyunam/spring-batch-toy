package com.mile.springbatchtoy.part4;

import com.mile.springbatchtoy.part5.Orders;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
public class SaveUserTasklet implements Tasklet {

    private final int SIZE = 100;
    private final UserRepository userRepository;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        List<Users> users = createUser();
        
        Collections.shuffle(users);

        userRepository.saveAll(users);

        return RepeatStatus.FINISHED;
    }

    private List<Users> createUser() {
        List<Users> users = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            users.add(Users.builder()
                    .orders(Collections.singletonList(Orders.builder()
                            .amount(1_000)
                            .createDate(LocalDate.of(2020, 12, 1))
                            .itemName("item" + i)
                            .build()))
                    .username("test username" + i)
                    .build());
        }
        for (int i = 0; i < SIZE; i++) {
            users.add(Users.builder()
                    .orders(Collections.singletonList(Orders.builder()
                            .amount(200_000)
                            .createDate(LocalDate.of(2020, 12, 2))
                            .itemName("item" + i)
                            .build()))
                    .username("test username" + i)
                    .build());
        }
        for (int i = 0; i < SIZE; i++) {
            users.add(Users.builder()
                    .orders(Collections.singletonList(Orders.builder()
                            .amount(300_000)
                            .createDate(LocalDate.of(2020, 12, 3))
                            .itemName("item" + i)
                            .build()))
                    .username("test username" + i)
                    .build());
        }
        for (int i = 0; i < SIZE; i++) {
            users.add(Users.builder()
                    .orders(Collections.singletonList(Orders.builder()
                            .amount(500_000)
                            .createDate(LocalDate.of(2020, 12, 4))
                            .itemName("item" + i)
                            .build()))
                    .username("test username" + i)
                    .build());
        }

        return users;
    }
}
