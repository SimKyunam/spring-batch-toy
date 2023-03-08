package com.mile.springbatchtoy.test1;

import com.mile.springbatchtoy.part3.Person;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.batch.item.support.builder.CompositeItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class CsvToH2Configuration {

    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;

    private final DataSource dataSource;
    private final EntityManagerFactory entityManagerFactory;

    @Bean
    public Job csvToH2ConvertJob() throws Exception {
        return this.jobBuilderFactory.get("csvToH2ConvertJob")
                .incrementer(new RunIdIncrementer())
                .start(this.csvToH2ConvertStep(null))
                .build();
    }

    @Bean
    @JobScope
    public Step csvToH2ConvertStep(@Value("#{jobParameter[allow_duplicate]}") String allowDuplicate) throws Exception {
        return this.stepBuilderFactory.get("csvToH2ConvertStep")
                .<Person, Person>chunk(10)
                .reader(csvToH2ItemReader())
                .processor(csvToH2ItemProcessor())
                .writer(csvToH2ItemWriter())
                .build();
    }

    private ItemWriter<Person> jpaItemWriter() throws Exception {
        JpaItemWriter<Person> itemWriter = new JpaItemWriterBuilder<Person>()
                .entityManagerFactory(entityManagerFactory)
                .build();

        itemWriter.afterPropertiesSet();
        return itemWriter;
    }

    private ItemWriter<Person> jdbcItemWriter() {
        JdbcBatchItemWriter<Person> itemWriter = new JdbcBatchItemWriterBuilder<Person>()
                .dataSource(dataSource)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql("insert into person(name, age, address) values(:name, :age, :address)")
                .build();

        itemWriter.afterPropertiesSet();

        return itemWriter;
    }

    private ItemWriter<? super Person> itemLogWriter() {
        return items -> items.forEach(x -> log.info("PERSON : {}", x));
    }

    private ItemWriter<? super Person> csvToH2ItemWriter() throws Exception {
        CompositeItemWriter<Person> personCompositeItemWriter = new CompositeItemWriter<Person>();

        List<ItemWriter<? super Person>> itemWriters = Arrays.asList(itemLogWriter(), jdbcItemWriter());
        personCompositeItemWriter.setDelegates(itemWriters);

        //builder로 생성하는 경우
//        CompositeItemWriter<Person> compositeItemBuild = new CompositeItemWriterBuilder<Person>()
//                .delegates(itemLogWriter(), jdbcItemWriter())
//                .build();

        personCompositeItemWriter.afterPropertiesSet();
        return personCompositeItemWriter;
    }

    private ItemProcessor<? super Person, ? extends Person> csvToH2ItemProcessor() {
        Map<String, Person> duplicateMap = new HashMap<>();
        return item -> {
            if(!duplicateMap.containsKey(item.getName())) {
                duplicateMap.put(item.getName(), item);
                return item;
            } else {
                return null;
            }
        };
    }

    private FlatFileItemReader<Person> csvToH2ItemReader() throws Exception {
        DefaultLineMapper<Person> lineMapper = new DefaultLineMapper<>();
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setNames("id", "name", "age", "address");
        lineMapper.setLineTokenizer(tokenizer);

        lineMapper.setFieldSetMapper(fieldSet -> {
            int id = fieldSet.readInt("id");
            String name = fieldSet.readString("name");
            String age = fieldSet.readString("age");
            String address = fieldSet.readString("address");

            return new Person(id, name, age, address);
        });

        FlatFileItemReader<Person> itemReader = new FlatFileItemReaderBuilder<Person>()
                .name("csvFileItemReader")
                .encoding("UTF-8")
                .resource(new ClassPathResource("csvToH2bulktest.csv"))
                .linesToSkip(1)
                .lineMapper(lineMapper)
                .build();

        //itemReader에서 필요한 필터설정 값이 잘 되어있는지 검증
        itemReader.afterPropertiesSet();

        return itemReader;
    }
}
