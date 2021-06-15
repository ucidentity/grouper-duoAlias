/**
 * @author jeffmc
 * $Id$
 */
package edu.berkeley.calnet.grouper.duoAlias;

import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.Map;


import edu.internet2.middleware.grouperDuo.GrouperDuoLog;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;

import com.duosecurity.client.Http;

import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.grouper.util.GrouperUtil;


/**
 *
 */
public class GrouperDuoAliasCommands {

  /**
   *
   * @param args
   */
  @SuppressWarnings("unused")
  public static void main(String[] args) {

//    for (GrouperDuoGroup grouperDuoGroup : retrieveGroups().values()) {
//      System.out.println(grouperDuoGroup);
//    }

//    createDuoGroup("test2", "testDesc", true);
//    updateDuoGroup("DG6LYPHI53Y8K50JJZYQ", "testDesc2", true);



    // mchyzer DU71ZRNO1W6507WQMJIP
//    System.out.println(retrieveDuoUserByIdOrUsername("mchyzer", false, null).getString("user_id"));

    String username = "mchyzer";
    String groupName = "test2";
//      assignUserToGroupIfNotInGroup(retrieveUserIdFromUsername(username), retrieveGroupIdFromGroupName(groupName), false);
//    removeUserFromGroup(retrieveDuoUserByIdOrUsername("mchyzer", false, null).getString("user_id"), retrieveGroups().get("test2").getId(), false);
//    System.out.println(userInGroup(retrieveDuoUserByIdOrUsername("mchyzer", false, null).getString("user_id"), retrieveGroups().get("test2").getId(), false));

//      deleteDuoGroup(retrieveGroupIdFromGroupName(groupName), false);

    deleteDuoGroup("DGVWQ4JEQIUE390MJLDD", false);

//      for (GrouperDuoUser grouperDuoUser : retrieveUsersForGroup(retrieveGroupIdFromGroupName(groupName)).values()) {
//        System.out.println(grouperDuoUser);
//      }

  }

  /**
   * @param username
   * @return the userId
   */
  public static String retrieveUserIdFromUsername(String username) {
    JSONObject duoUser = retrieveDuoUserByIdOrUsername(username, false, null);
    return (duoUser == null || !duoUser.has("user_id")) ? null : duoUser.getString("user_id");
  }

  /**
   * assign duo user alias
   * @param theId
   * @param aliasId
   * @param aliasValue
   * @return boolean (true if the alias was set)
   **/
  public static boolean assignDuoUserAlias(String userId, String aliasId, String aliasValue) {
    return assignDuoUserAlias(userId, aliasId, aliasValue, null);
  }

  /**
   *
   */
  public GrouperDuoAliasCommands() {
  }

  /**
   * get the http for duo and set the url,
   * @param method
   * @param path
   * @return the http
   */
  private static Http httpAdmin(String method, String path) {
    return httpAdmin(method, path, null);
  }

  /**
   * get the http for duo and set the url,
   * @param method
   * @param path
   * @param timeoutSeconds
   * @return the http
   */
  private static Http httpAdmin(String method, String path, Integer timeoutSeconds) {

    String domain = GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired("grouperDuo.adminDomainName");

    Http request = (timeoutSeconds != null && timeoutSeconds > 0) ?
            new Http(method, domain, path, timeoutSeconds) : new Http(method, domain, path);

    return request;
  }

  /**
   * execute response raw without checked exception
   * @param request
   * @return the string
   */
  private static String executeRequestRaw(Http request) {
    try {
      return request.executeRequestRaw();
    } catch (Exception e) {
      throw new RuntimeException("Problem with duo", e);
    }
  }

  /**
   * sign the http request
   * @param request
   */
  private static void signHttpAdmin(Http request) {
    String integrationKey = GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired("grouperDuo.adminIntegrationKey");
    String secretKey = GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired("grouperDuo.adminSecretKey");
    try {
      request.signRequest(integrationKey,
              secretKey);
    } catch (UnsupportedEncodingException uee) {
      throw new RuntimeException("Error signing request", uee);
    }

  }

  /**
   * retrieve duo user
   * @param theId
   * @param isDuoUuid true if id, false if username
   * @param timeoutSeconds null if no timeout
   * @return the json object
   */
  public static JSONObject retrieveDuoUserByIdOrUsername(String theId, boolean isDuoUuid, Integer timeoutSeconds) {

    Map<String, Object> debugMap = new LinkedHashMap<String, Object>();

    debugMap.put("method", "retrieveDuoUserByIdOrUsername");
    if (isDuoUuid) {
      debugMap.put("userId", theId);
    } else {
      debugMap.put("username", theId);
    }
    long startTime = System.nanoTime();
    try {

      if (StringUtils.isBlank(theId)) {
        throw new RuntimeException("Why is netId blank?");
      }

      //retrieve user
      String path = "/admin/v1/users" + (isDuoUuid ? ("/" + theId) : "");
      debugMap.put("GET", path);
      Http request = httpAdmin("GET", path, timeoutSeconds);

      if (!isDuoUuid) {
        request.addParam("username", theId);
      }

      signHttpAdmin(request);

      String result = executeRequestRaw(request);

      //  {
      //    "response":[
      //      {
      //        "desktoptokens":[
      //
      //        ],
      //        "email":"",
      //        "groups":[
      //
      //        ],
      //        "last_login":null,
      //        "notes":"",
      //        "phones":[
      //
      //        ],
      //        "realname":"",
      //        "status":"active",
      //        "tokens":[
      //
      //        ],
      //        "user_id":"DUXEK2QS0MSI7TV3TEN1",
      //        "username":"harveycg"
      //      }
      //    ],
      //    "stat":"OK"
      //  }

      // {"code": 40401, "message": "Resource not found", "stat": "FAIL"}

      JSONObject jsonObject = (JSONObject) JSONSerializer.toJSON( result );

      if (jsonObject.has("code") && jsonObject.getInt("code") == 40401) {
        debugMap.put("code", 40401);
        return null;
      }

      if (!StringUtils.equals(jsonObject.getString("stat"), "OK")) {
        debugMap.put("error", true);
        debugMap.put("result", result);
        throw new RuntimeException("Bad response from Duo: " + result + ", " + theId);
      }
      Object response = jsonObject.get("response");
      JSONObject duoUser = null;
      if (response instanceof JSONObject) {
        duoUser = (JSONObject)response;
      } else {
        JSONArray responseArray = (JSONArray)response;
        if (responseArray.size() > 0) {
          if (responseArray.size() > 1) {
            throw new RuntimeException("Why more than 1 user found? " + responseArray.size() + ", " + result);
          }
          duoUser = (JSONObject)responseArray.get(0);
        }
      }
      if (duoUser != null) {
        debugMap.put("returnedUserId", duoUser.getString("user_id"));
        debugMap.put("returnedUsername", duoUser.getString("username"));
      }
      return duoUser;
    } catch (RuntimeException re) {
      debugMap.put("exception", ExceptionUtils.getFullStackTrace(re));
      throw re;
    } finally {
      GrouperDuoLog.duoLog(debugMap, startTime);
    }
  }



  /**
   * assign duo user alias
   * @param theId
   * @param aliasId
   * @param aliasValue
   * @param timeoutSeconds null if no timeout
   * @return boolean (true if the alias was set)
   */
  public static boolean assignDuoUserAlias(String userId, String aliasId, String aliasValue, Integer timeoutSeconds) {

    Map<String, Object> debugMap = new LinkedHashMap<String, Object>();

    debugMap.put("method", "assignDuoUserAlias");
    debugMap.put("userId", userId);
    debugMap.put("AliasId", aliasId);
    debugMap.put("Alias", aliasValue);

    long startTime = System.nanoTime();
    try {

      if (StringUtils.isBlank(userId)) {
        throw new RuntimeException("Why is userId blank?");
      }

      //assign alias to user
      // POST /admin/v1/users/[user_id]?aliasId=alias
      String path = "/admin/v1/users/" + userId;
      debugMap.put("POST", path);
      Http request = httpAdmin("POST", path);
      //request.addParam("aliases", "{" + \"aliasId\" + ":" + alias + "}");

      JSONObject aliasParam = new JSONObject();
      aliasParam.put(aliasId, alias);
      //request.addParam( "aliases", "\"" + aliasParam.toString() + "\"" );
      //System.out.print("\n\n");
      String formatedAliases = String.format("%s=%s", aliasId, aliasValue);
      System.out.println(formatedAliases);
      request.addParam( "aliases", formatedAliases );
      //request.addParam( "aliases", "alias3=jeffreym-test3&alias5=jeffreym-test5");

      signHttpAdmin(request);

      String result = executeRequestRaw(request);

      JSONObject jsonObject = (JSONObject) JSONSerializer.toJSON( result );

      if (jsonObject.has("code") && jsonObject.getInt("code") == 40401) {
        debugMap.put("code", 40401);
        return false;
      }

      if (!StringUtils.equals(jsonObject.getString("stat"), "OK")) {
        debugMap.put("error", true);
        debugMap.put("result", result);
        throw new RuntimeException("Bad response from Duo: " + result + ", " + userId);
      }
      JSONObject duoUser = jsonObject.get("response");

      if (duoUser != null) {
        if (duoUser.getJSONObject("aliases").get(aliasId).equals(aliasValue)){
          return true;
        }
      }
      return false;

    } catch (RuntimeException re) {
      debugMap.put("exception", ExceptionUtils.getFullStackTrace(re));
      throw re;
    }
  }




}
