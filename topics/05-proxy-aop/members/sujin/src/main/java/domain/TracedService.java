package domain;

import org.springframework.stereotype.Service;

@Service
public class TracedService {

    @Traced
    public String ok() { return "결과"; }

    @Traced
    public String fail() { throw new RuntimeException("일부러 실패"); }
}
