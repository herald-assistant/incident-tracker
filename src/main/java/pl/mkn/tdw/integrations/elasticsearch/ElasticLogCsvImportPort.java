package pl.mkn.tdw.integrations.elasticsearch;

import pl.mkn.tdw.integrations.elasticsearch.contract.ElasticLogCsvImportResult;

import java.io.InputStream;

public interface ElasticLogCsvImportPort {

    ElasticLogCsvImportResult importCsv(InputStream inputStream);
}
