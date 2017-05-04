package uk.ac.ebi.biosamples.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import uk.ac.ebi.biosamples.model.Attribute;

/*

"characteristics": {
    "material": [
      {
        "text": "specimen from organism",
        "ontologyTerms": [
          "http://purl.obolibrary.org/obo/OBI_0001479"
        ]
      }
    ],
    "specimenCollectionDate": [
      {
        "text": "2013-05",
        "unit": "YYYY-MM"
      }
    ],

 */
public class CharacteristicDeserializer extends StdDeserializer<SortedSet> {

	private Logger log = LoggerFactory.getLogger(this.getClass());

	public CharacteristicDeserializer(){
		this(SortedSet.class);
	}

	public CharacteristicDeserializer(Class<SortedSet> t) {
		super(t);
	}

	@Override
	public SortedSet deserialize(JsonParser p, DeserializationContext ctxt)
			throws IOException, JsonProcessingException {
		
		SortedSet<Attribute> attributes = new TreeSet<>();
		Map<String, List<LegacyAttribute>> characteristics = p.readValueAs(new TypeReference<Map<String, List<LegacyAttribute>>>(){});
		for (String type : characteristics.keySet()) {
			for (LegacyAttribute legacy : characteristics.get(type)) {				
				String iri = null;
				if (legacy.ontologyTerms != null) {
					if (legacy.ontologyTerms.size() > 1) {
						throw new JsonMappingException(p,"Must have only one ontology term per attribute");
					} else {
						iri = legacy.ontologyTerms.get(0).trim();
					}
				}
				String unit = legacy.unit;
				
				attributes.add(Attribute.build(type, legacy.text, iri, unit));
			}
		}
		
		return attributes;
	}

	/**
	 * This internal class is used for easier deserializtion with jackson. The fields
	 * match the field names in the JSON and therefore should not be changed!
	 * 
	 * @author faulcon
	 *
	 */
	private static class LegacyAttribute {
		
		public String text;
		public List<String> ontologyTerms;
		public String unit;
 		
		
	}
	
}