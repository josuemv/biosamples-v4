package uk.ac.ebi.biosamples.solr.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.solr.core.SolrOperations;
import org.springframework.data.solr.core.query.Criteria;
import org.springframework.data.solr.core.query.FacetOptions;
import org.springframework.data.solr.core.query.FacetQuery;
import org.springframework.data.solr.core.query.Field;
import org.springframework.data.solr.core.query.FilterQuery;
import org.springframework.data.solr.core.query.Query;
import org.springframework.data.solr.core.query.SimpleFacetQuery;
import org.springframework.data.solr.core.query.SimpleFilterQuery;
import org.springframework.data.solr.core.query.SimpleQuery;
import org.springframework.data.solr.core.query.SimpleStringCriteria;
import org.springframework.data.solr.core.query.result.FacetFieldEntry;
import org.springframework.data.solr.core.query.result.FacetPage;
import org.springframework.data.solr.core.query.result.FacetQueryResult;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import uk.ac.ebi.biosamples.solr.model.SampleFacets;
import uk.ac.ebi.biosamples.solr.model.SampleFacets.SampleFacetsBuilder;
import uk.ac.ebi.biosamples.solr.model.SolrSample;
import uk.ac.ebi.biosamples.solr.repo.SolrSampleRepository;

@Service
public class SolrSampleService {

	private final SolrSampleRepository solrSampleRepository;	

	private Logger log = LoggerFactory.getLogger(getClass());
	
	public SolrSampleService(SolrSampleRepository solrSampleRepository) {
		this.solrSampleRepository = solrSampleRepository;
	}		
	
	//TODO add caching
	public Page<SolrSample> fetchSolrSampleByText(String searchTerm, MultiValueMap<String,String> filters, Pageable pageable) {
		//default to search all
		if (searchTerm == null || searchTerm.trim().length() == 0) {
			searchTerm = "*:*";
		}
		//build a query out of the users string and any facets
		Query query = new SimpleQuery(searchTerm);
				
		if (filters != null) {
			query = addFilters(query, filters);
		}		
		
		// return the samples from solr that match the query
		return solrSampleRepository.findByQuery(query, pageable);
	}	
		
	public SampleFacets getFacets(String searchTerm, MultiValueMap<String,String> filters, Pageable facetPageable, Pageable facetValuePageable) {
		//default to search all
		if (searchTerm == null || searchTerm.trim().length() == 0) {
			searchTerm = "*:*";
		}
		
		SampleFacetsBuilder builder = new SampleFacetsBuilder();

		//build a query out of the users string and any facets
		FacetQuery query = new SimpleFacetQuery();
		query.addCriteria(new Criteria().expression(searchTerm));
		query = addFilters(query, filters);
		Page<FacetFieldEntry> facetFields = solrSampleRepository.getFacetFields(query, facetPageable);

		//using the query, get a list of facets and overall counts
		List<String> facetFieldList = new ArrayList<>();
		for (FacetFieldEntry ffe : facetFields) {
			log.info("Putting "+ffe.getValue()+" with count "+ffe.getValueCount());
			facetFieldList.add(ffe.getValue());				
			builder.addFacet(ffe.getValue(), ffe.getValueCount());
		}
		
		//if there are no facets avaliable (e.g. no samples)
		//then cleanly exit here
		if (facetFieldList.isEmpty()) {
			return builder.build();
		}

		FacetPage<?> facetPage = solrSampleRepository.getFacets(query, facetFieldList, facetValuePageable);
		for (Field field : facetPage.getFacetFields()) {

			//for each value, put the number of them into this facets map
			for (FacetFieldEntry ffe : facetPage.getFacetResultPage(field)) {
				log.info("Adding "+field.getName()+" : "+ffe.getValue()+" with count "+ffe.getValueCount());					
				builder.addFacetValue(field.getName(), ffe.getValue(), ffe.getValueCount());
			}
		}
		
		return builder.build();
		
	}
	
	private <T extends Query> T addFilters(T query, MultiValueMap<String,String> filters) {
		//if no filters or filters are null, quick exit
		if (filters == null || filters.size() == 0) {
			return query;
		}		

		boolean filter = false;
		FilterQuery filterQuery = new SimpleFilterQuery();
		for (String facetType : filters.keySet()) {
			Criteria facetCriteria = null;
			
			String facetField = attributeTypeToField(facetType);
			for(String facatValue : filters.get(facetType)) {
				if (facatValue == null) {
					//no specific value, check if its not null
					facetCriteria = new Criteria(facetField).isNotNull();					
				} else if (facetCriteria == null) {
					facetCriteria = new Criteria(facetField).is(facatValue);
				} else {
					facetCriteria = facetCriteria.or(new Criteria(facetField).is(facatValue));
				}

				log.info("Filtering on "+facetField+" for value "+facatValue);
			}
			if (facetCriteria != null) {
				filterQuery.addCriteria(facetCriteria);
				filter = true;
			}
		}	
		if (filter) {
			query.addFilterQuery(filterQuery);
		}
		return query;
	}
	
	
	public static String attributeTypeToField(String type) {
		type = type.replaceAll(" ", "_");
		type = type+"_av_ss";
		return type;
	}
	
	public static String fieldToAttributeType(String field) {
		//strip _av_ss
		field = field.substring(0, field.length()-6);
		field = field.replaceAll("_", " ");
		return field;
	}
}