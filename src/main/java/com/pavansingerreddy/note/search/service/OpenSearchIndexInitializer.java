package com.pavansingerreddy.note.search.service;

import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.generic.OpenSearchGenericClient;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.opensearch.generic.Response;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.opensearch.indices.UpdateAliasesRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OpenSearchIndexInitializer implements ApplicationRunner {

  private final OpenSearchClient openSearchClient;

  @Value("${opensearch.index-name:notes_v1}")
  private String indexName;

  @Value("${opensearch.search-alias:notes_search}")
  private String searchAlias;

  @Value("${opensearch.write-alias:notes_write}")
  private String writeAlias;

  public OpenSearchIndexInitializer(OpenSearchClient openSearchClient) {
    this.openSearchClient = openSearchClient;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      boolean exists = openSearchClient.indices().exists(
          ExistsRequest.of(e -> e.index(indexName))).value();

      if (!exists) {
        log.info("Creating OpenSearch index '{}' with custom analyzers and aliases...", indexName);
        createNotesIndex();
        createAliases();
        log.info("OpenSearch index '{}' initialized successfully.", indexName);
      } else {
        log.info("OpenSearch index '{}' already exists.", indexName);
      }
    } catch (Exception e) {
      log.warn("OpenSearch is not currently available or failed to initialize index '{}'. " +
          "Search queries will automatically fallback to database SQL search. Error: {}", indexName, e.getMessage());
    }
  }

  private void createNotesIndex() throws Exception {
    String indexSettingsAndMappingsJson = """
        {
          "settings": {
            "index": {
              "number_of_shards": 2,
              "number_of_replicas": 1
            },
            "analysis": {
              "analyzer": {
                "note_content_analyzer": {
                  "type": "custom",
                  "tokenizer": "standard",
                  "filter": ["lowercase", "asciifolding", "english_stop", "english_stemmer"]
                },
                "autocomplete_index_analyzer": {
                  "type": "custom",
                  "tokenizer": "standard",
                  "filter": ["lowercase", "asciifolding", "edge_ngram_filter"]
                },
                "autocomplete_search_analyzer": {
                  "type": "custom",
                  "tokenizer": "standard",
                  "filter": ["lowercase", "asciifolding"]
                }
              },
              "filter": {
                "english_stop": {
                  "type": "stop",
                  "stopwords": "_english_"
                },
                "english_stemmer": {
                  "type": "stemmer",
                  "language": "english"
                },
                "edge_ngram_filter": {
                  "type": "edge_ngram",
                  "min_gram": 2,
                  "max_gram": 15
                }
              }
            }
          },
          "mappings": {
            "properties": {
              "noteId": { "type": "long" },
              "userId": { "type": "keyword" },
              "title": {
                "type": "text",
                "analyzer": "note_content_analyzer",
                "index_options": "offsets",
                "fields": {
                  "autocomplete": {
                    "type": "text",
                    "analyzer": "autocomplete_index_analyzer",
                    "search_analyzer": "autocomplete_search_analyzer"
                  },
                  "keyword": { "type": "keyword", "ignore_above": 256 }
                }
              },
              "content": {
                "type": "text",
                "analyzer": "note_content_analyzer",
                "index_options": "offsets"
              },
              "createdAt": { "type": "date" },
              "updatedAt": { "type": "date" }
            }
          }
        }
        """;

    OpenSearchGenericClient genericClient = openSearchClient.generic();
    try (Response response = genericClient.execute(
        Requests.builder()
            .endpoint("/" + indexName)
            .method("PUT")
            .json(indexSettingsAndMappingsJson)
            .build())) {
      log.info("Index creation response status: {}", response.getStatus());
    }
  }

  private void createAliases() throws Exception {
    UpdateAliasesRequest aliasesRequest = UpdateAliasesRequest.of(u -> u
        .actions(act -> act.add(add -> add.index(indexName).alias(searchAlias)))
        .actions(act -> act.add(add -> add.index(indexName).alias(writeAlias))));
    openSearchClient.indices().updateAliases(aliasesRequest);
  }
}
