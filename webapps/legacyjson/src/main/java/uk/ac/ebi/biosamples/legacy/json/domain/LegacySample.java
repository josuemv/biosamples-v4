package uk.ac.ebi.biosamples.legacy.json.domain;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.hateoas.core.Relation;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import uk.ac.ebi.biosamples.legacy.json.service.LegacyJsonUtilities;
import uk.ac.ebi.biosamples.model.Attribute;
import uk.ac.ebi.biosamples.model.Contact;
import uk.ac.ebi.biosamples.model.Organization;
import uk.ac.ebi.biosamples.model.Publication;
import uk.ac.ebi.biosamples.model.Sample;


@Relation(value="sample", collectionRelation = "samples")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonPropertyOrder(value = {"accession", "name", "releaseDate", "updateDate", "description", "characteristics"})
public class LegacySample {

    @JsonIgnore
    private final Sample sample;

    private MultiValueMap<String, LegacyAttribute> characteristics;
    private List<LegacyExternalReference> externalReferences;
    private String description;


    public LegacySample(Sample sample) {
        this.sample = sample;
        hydrateLegacySample(sample);
    }

    private void hydrateLegacySample(Sample sample) {

        this.description = extractSampleDescription(sample).orElse("");
        this.characteristics = extractCharacteristics(sample);
        this.externalReferences = extractExternalReferences(sample);
    }

    private List<LegacyExternalReference> extractExternalReferences(Sample sample) {

        return this.sample.getExternalReferences().stream()
                .map(LegacyExternalReference::new)
                .collect(Collectors.toList());
    }

    private Optional<String> extractSampleDescription(Sample sample) {
        Optional<Attribute> descriptionAttribute = this.sample.getAttributes().stream()
                .filter(a -> a.getType().equalsIgnoreCase("description"))
                .findAny();
        return descriptionAttribute.map(Attribute::getValue);
    }

    private MultiValueMap<String, LegacyAttribute> extractCharacteristics(Sample sample) {
        Map<String, List<Attribute>> attributesByType = sample.getAttributes()
                .stream()
                .filter(((Predicate<Attribute>)this::isDescription).negate())
                .collect(Collectors.groupingBy(Attribute::getType));

        MultiValueMap<String, LegacyAttribute> legacyAttributesByType = new LinkedMultiValueMap<>();
        for (String type: attributesByType.keySet()) {
            legacyAttributesByType.put(LegacyJsonUtilities.camelCaser(type),
                    attributesByType.get(type)
                            .stream()
                            .map(LegacyAttribute::new)
                            .collect(Collectors.toList()));
        }
        return legacyAttributesByType;
    }

    @JsonGetter
    public String accession() {
        return this.sample.getAccession();
    }

    @JsonGetter
    public String name() {
        return this.sample.getName();
    }

    @JsonGetter
    public String description() {
        return description;
    }

    @JsonGetter
    @JsonSerialize(using = LocalDateSerializer.class)
    public Instant updateDate() {
        return this.sample.getUpdate();
    }

    @JsonGetter
    @JsonSerialize(using = LocalDateSerializer.class)
    public Instant releaseDate() {
        return this.sample.getRelease();
    }

    @JsonGetter
    public MultiValueMap<String, LegacyAttribute> characteristics() {
        return this.characteristics;
    }

    @JsonGetter
    public List<LegacyExternalReference> externalReferences() {
        return externalReferences;
    }

    private boolean isDescription(uk.ac.ebi.biosamples.model.Attribute attribute) {
        return attribute.getType().equalsIgnoreCase("description");
    }

    @JsonGetter("organization")
    public List<Organization> organization() {
        return new ArrayList<>(this.sample.getOrganizations());
    }

    @JsonGetter("contact")
    public List<Contact> contacts() {
        return new ArrayList(this.sample.getContacts());
    }

    @JsonGetter("publications")
    public List<Publication> publications() {
        return new ArrayList(this.sample.getPublications());
    }

    private static class  LocalDateSerializer extends StdSerializer<Instant> {

        private static final long serialVersionUID = 1L;

        protected LocalDateSerializer() {
            super(Instant.class);
        }


        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
    }


//    private String camelcaser(String value) {
//        StringBuilder finalString = new StringBuilder();
//        value = value.replaceAll("[^A-Za-z0-9]"," ").replace("\\s+"," ");
//        for(String part: value.split(" ")) {
////            part = part.replaceAll("[^A-Za-z0-9]","");
//            part = StringUtils.capitalize(part.toLowerCase());
//            if (finalString.length() == 0) {
//                finalString.append(part.toLowerCase());
//                continue;
//            }
//            finalString.append(part);
//        }
//        return finalString.toString();
//    }
}
