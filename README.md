# grouper.duo-alias
Welcome to Grouper Duo Alias. This project uses a grouper changelog consumer to populate user account aliases. Jexl expressions are used to pull account information for the aliases. Aliases need to be unique both from each other and from the main account name. If a standard prefix or suffice is needed, add it outside the brackets. 

Example:

grouperDuoAlias.alias1.expression = myPrefix-${subject.getAttributeValue("id")}

Both the groupName and expression need to be present. 

Example:

grouperDuoAlias.alias1.expression = myPrefix${subject.getAttributeValue("id")}
grouperDuoAlias.alias1.groupName = someStem:myAliasgroup1

When a member is added to an alias group, the alias is set. When a member is deleted, the alias is removed. 

When the full refresh runs, it will look for deletes that have happened in the past to confirm the alias values have been removed. 
To compile use:

 mvn clean package dependency:copy-dependencies -DincludeScope=runtime
 
 