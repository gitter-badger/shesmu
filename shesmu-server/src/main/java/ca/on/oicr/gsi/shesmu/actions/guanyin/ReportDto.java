package ca.on.oicr.gsi.shesmu.actions.guanyin;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.actions.rest.ParameterInfo;
import ca.on.oicr.gsi.shesmu.actions.util.JsonParameter;
import ca.on.oicr.gsi.shesmu.compiler.Parser;

public class ReportDto {
	private String category;
	private String name;
	private Map<String, ParameterInfo> permittedParameters;
	private long id;
	private String version;

	public String getCategory() {
		return category;
	}

	@JsonProperty("report_id")
	public long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	@JsonProperty("permitted_parameters")
	public Map<String, ParameterInfo> getPermittedParameters() {
		return permittedParameters;
	}

	public String getVersion() {
		return version;
	}

	public boolean isValid() {
		return Parser.IDENTIFIER.matcher(category).matches() && Parser.IDENTIFIER.matcher(name).matches();
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public void setId(long id) {
		this.id = id;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setPermittedParameters(Map<String, ParameterInfo> permittedParameters) {
		this.permittedParameters = permittedParameters;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public ReportDefinition toDefinition(String 观音Url, String drmaaUrl, String drmaaPsk) {
		return new ReportDefinition(观音Url, drmaaUrl, drmaaPsk, category, name, version,
				permittedParameters.entrySet().stream().map(e -> new JsonParameter(e.getKey(),
						Imyhat.parse(e.getValue().getType()), e.getValue().isRequired())));
	}

}