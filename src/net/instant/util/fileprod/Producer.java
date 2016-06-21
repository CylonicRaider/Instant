package net.instant.util.fileprod;

import java.io.FileNotFoundException;

public interface Producer {

    ProducerJob produce(String name);

}
