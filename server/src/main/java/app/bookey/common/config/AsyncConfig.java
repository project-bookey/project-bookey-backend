package app.bookey.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

/** 도서 메타 보강 워커 등 논블로킹 작업용 (§F1 검색 파이프라인 3번). */
@Configuration
public class AsyncConfig {

    @Bean("bookMetaExecutor")
    public TaskExecutor bookMetaExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("book-meta-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(8);
        return executor;
    }
}
