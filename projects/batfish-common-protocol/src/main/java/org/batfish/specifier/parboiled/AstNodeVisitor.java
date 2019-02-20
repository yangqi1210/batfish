package org.batfish.specifier.parboiled;

interface AstNodeVisitor<T> {
  T visitAddressGroupIpSpaceAstNode(AddressGroupIpSpaceAstNode addressGroupIpSpaceAstNode);

  T visitCommaIpSpaceAstNode(CommaIpSpaceAstNode commaIpSpaceAstNode);

  T visitIpAstNode(IpAstNode ipAstNode);

  T visitIpWildcardAstNode(IpWildcardAstNode ipWildcardAstNode);

  T visitPrefixAstNode(PrefixAstNode prefixAstNode);

  T visitIpRangeAstNode(IpRangeAstNode rangeIpSpaceAstNode);

  T visitStringAstNode(StringAstNode stringAstNode);

  T visitUnionInterfaceAstNode(UnionInterfaceAstNode unionInterfaceAstNode);

  T visitDifferenceInterfaceAstNode(DifferenceInterfaceAstNode differenceInterfaceAstNode);

  T visitConnectedToInterfaceAstNode(ConnectedToInterfaceAstNode connectedToInterfaceAstNode);

  T visitTypeInterfaceAstNode(TypeInterfaceAstNode typeInterfaceAstNode);

  T visitNameInterfaceAstNode(NameInterfaceAstNode nameInterfaceAstNode);

  T visitNameRegexInterfaceAstNode(NameRegexInterfaceAstNode nameRegexInterfaceAstNode);

  T visitVrfInterfaceAstNode(VrfInterfaceAstNode vrfInterfaceAstNode);

  T visitZoneInterfaceAstNode(ZoneInterfaceAstNode zoneInterfaceAstNode);

  T visitInterfaceGroupInterfaceAstNode(
      InterfaceGroupInterfaceAstNode interfaceGroupInterfaceAstNode);

  T visitIntersectionInterfaceAstNode(IntersectionInterfaceAstNode intersectionInterfaceAstNode);

  T visitUnionNodeAstNode(UnionNodeAstNode unionNodeAstNode);

  T visitDifferenceNodeAstNode(DifferenceNodeAstNode differenceNodeAstNode);

  T visitIntersectionNodeAstNode(IntersectionNodeAstNode intersectionNodeAstNode);

  T visitRoleNodeAstNode(RoleNodeAstNode roleNodeAstNode);

  T visitNameNodeAstNode(NameNodeAstNode nameNodeAstNode);

  T visitNameRegexNodeAstNode(NameRegexNodeAstNode nameRegexNodeAstNode);

  T visitTypeNodeAstNode(TypeNodeAstNode typeNodeAstNode);
}