package com.mile.springbatchtoy.part6;

import com.mile.springbatchtoy.part4.LevelUpJobExecutionListener;
import com.mile.springbatchtoy.part4.SaveUserTasklet;
import com.mile.springbatchtoy.part4.UserRepository;
import com.mile.springbatchtoy.part4.Users;
import com.mile.springbatchtoy.part5.JobParametersDecide;
import com.mile.springbatchtoy.part5.OrderStatistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.partition.PartitionHandler;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.transform.BeanWrapperFieldExtractor;
import org.springframework.batch.item.file.transform.DelimitedLineAggregator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.TaskExecutor;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class PartitionUserConfiguration {

    private final String JOB_NAME = "partitionUserJob";
    private final int CHUNK = 1000;
    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;
    private final UserRepository userRepository;
    private final EntityManagerFactory entityManagerFactory;
    private final DataSource dataSource;
    private final TaskExecutor taskExecutor;

    @Bean(JOB_NAME)
    public Job userJob() throws Exception {
        return this.jobBuilderFactory.get(JOB_NAME)
                .incrementer(new RunIdIncrementer())
                .start(this.saveUserStep())
                .next(this.userLevelUpManagerStep())
                .listener(new LevelUpJobExecutionListener(userRepository))
                .next(new JobParametersDecide("date"))
                    .on(JobParametersDecide.CONTINUE.getName())
                    .to(this.orderStatisticsStep(null))
                    .build()
                .build();
    }

    @Bean(JOB_NAME + "_orderStatisticsStep")
    @JobScope
    public Step orderStatisticsStep(@Value("#{jobParameters[date]}") String date) throws Exception {
        return this.stepBuilderFactory.get(JOB_NAME + "_orderStatisticsStep")
                .<OrderStatistics, OrderStatistics>chunk(CHUNK)
                .reader(orderStatisticsItemReader(date))
                .writer(orderStatisticsItemWriter(date))
                .build();
    }

    private ItemWriter<? super OrderStatistics> orderStatisticsItemWriter(String date) throws Exception {
        YearMonth yearMonth = YearMonth.parse(date);
        String fileName = yearMonth.getYear() + "년_" + yearMonth.getMonthValue() + "월_일별_주문_금액.csv";

        BeanWrapperFieldExtractor<OrderStatistics> fieldExtractor = new BeanWrapperFieldExtractor<>();
        fieldExtractor.setNames(new String[] {"amount", "date"});

        DelimitedLineAggregator<OrderStatistics> lineAggregator = new DelimitedLineAggregator<>();
        lineAggregator.setDelimiter(",");
        lineAggregator.setFieldExtractor(fieldExtractor);

        FlatFileItemWriter<OrderStatistics> itemWriter = new FlatFileItemWriterBuilder<OrderStatistics>()
                .resource(new FileSystemResource("output/" + fileName))
                .lineAggregator(lineAggregator)
                .name(JOB_NAME + "_orderStatisticsItemWriter")
                .encoding("UTF-8")
                .headerCallback(writer -> writer.write("total_amount,date"))
                .build();

        itemWriter.afterPropertiesSet();
        return itemWriter;
    }

    private ItemReader<? extends OrderStatistics> orderStatisticsItemReader(String date) throws Exception {
        YearMonth yearMonth = YearMonth.parse(date);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("startDate", yearMonth.atDay(1));
        parameters.put("endDate", yearMonth.atEndOfMonth());

        Map<String, Order> sortKey = new HashMap<>();
        sortKey.put("create_date", Order.ASCENDING);

        JdbcPagingItemReader<OrderStatistics> itemReader = new JdbcPagingItemReaderBuilder<OrderStatistics>()
                .dataSource(this.dataSource)
                .rowMapper((rs, rowNum) -> OrderStatistics.builder()
                        .amount(rs.getString(1))
                        .date(LocalDate.parse(rs.getString(2), DateTimeFormatter.ISO_DATE))
                        .build())
                .pageSize(CHUNK)
                .name(JOB_NAME + "_orderStatisticsItemReader")
                .selectClause("sum(amount), create_date")
//                .selectClause("amount, create_date")
                .fromClause("orders")
                .whereClause("create_date >= :startDate and create_date <= :endDate")
                .groupClause("create_date")
                .parameterValues(parameters)
                .sortKeys(sortKey)
                .build();

        itemReader.afterPropertiesSet();
        return itemReader;
    }

    @Bean(JOB_NAME + "_saveUserStep")
    public Step saveUserStep() {
        return this.stepBuilderFactory.get(JOB_NAME + "_saveUserStep")
                .tasklet(new SaveUserTasklet(userRepository))
                .build();
    }

    @Bean(JOB_NAME + "_userLevelUpStep")
    public Step userLevelUpStep() throws Exception {
        return this.stepBuilderFactory.get(JOB_NAME + "_userLevelUpStep")
                .<Users, Future<Users>>chunk(CHUNK)
                .reader(itemReader(null, null))
                .processor(itemProcessor())
                .writer(itemWriter())
                .build();
    }

    @Bean(JOB_NAME + "_userLevelUpStep.manager")
    public Step userLevelUpManagerStep() throws Exception {
        return this.stepBuilderFactory.get(JOB_NAME + "_userLevelUpStep.manager")
                .partitioner(JOB_NAME + "_userLevelUpStep", new UserLevelUpPartitioner(userRepository))
                .step(userLevelUpStep())
                .partitionHandler(taskExecutorPartitionHandler())
                .build();
    }

    @Bean(JOB_NAME + "taskExecutorPartitionHandler")
    PartitionHandler taskExecutorPartitionHandler() throws Exception {
        TaskExecutorPartitionHandler handler = new TaskExecutorPartitionHandler();
        handler.setStep(userLevelUpStep());
        handler.setTaskExecutor(this.taskExecutor);
        handler.setGridSize(8);

        return handler;
    }

    private AsyncItemWriter<Users> itemWriter() {
        ItemWriter<Users> itemWriter = users -> users.forEach(x -> {
            x.levelUp();
            userRepository.save(x);
        });

        AsyncItemWriter<Users> asyncItemWriter = new AsyncItemWriter<>();
        asyncItemWriter.setDelegate(itemWriter);

        return asyncItemWriter;
    }

    private AsyncItemProcessor<Users, Users> itemProcessor() {
        ItemProcessor<Users, Users> itemProcessor = user-> {
            if(user.availableLevelUp()) {
                return user;
            }

            return null;
        };

        AsyncItemProcessor<Users, Users> asyncItemProcessor = new AsyncItemProcessor<>();
        asyncItemProcessor.setDelegate(itemProcessor);
        asyncItemProcessor.setTaskExecutor(this.taskExecutor);

        return asyncItemProcessor;
    }

    @Bean
    @StepScope
    JpaPagingItemReader<? extends Users> itemReader(@Value("#{stepExecutionContext[minId]}") Long minId,
                                           @Value("#{stepExecutionContext[maxId]}") Long maxId) throws Exception {
        Map<String, Object> paramters = new HashMap<>();
        paramters.put("minId", minId);
        paramters.put("maxId", maxId);

        JpaPagingItemReader<Users> itemReader = new JpaPagingItemReaderBuilder<Users>()
                .queryString("select u from Users u where u.id between :minId and :maxId")
                .parameterValues(paramters)
                .entityManagerFactory(entityManagerFactory)
                .pageSize(CHUNK) //일반적으로 chunkSize와 동일
                .name(JOB_NAME + "_userItemReader")
                .build();

        itemReader.afterPropertiesSet();
        return itemReader;
    }
}
