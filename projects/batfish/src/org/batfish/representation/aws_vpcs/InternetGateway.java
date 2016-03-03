package org.batfish.representation.aws_vpcs;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

import org.batfish.common.BatfishLogger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public class InternetGateway implements AwsVpcConfigElement, Serializable {

	private static final long serialVersionUID = 1L;

	private String _internetGatewayId;

	private List<String> _attachmentVpcIds = new LinkedList<String>();

	public InternetGateway(JSONObject jObj, BatfishLogger logger) throws JSONException {
		_internetGatewayId = jObj.getString(JSON_KEY_INTERNET_GATEWAY_ID);

	      JSONArray attachments = jObj.getJSONArray(JSON_KEY_ATTACHMENTS);
	      for (int index = 0; index < attachments.length(); index++) {
	          JSONObject childObject = attachments.getJSONObject(index);
	          _attachmentVpcIds.add(childObject.getString(JSON_KEY_VPC_ID));         
	       }

	}
	
	@Override
	public String getId() {
		return _internetGatewayId;
	}
}
