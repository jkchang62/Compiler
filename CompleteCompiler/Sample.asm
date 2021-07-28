  0         LOADL        0
  1         CALL         newarr  
  2         CALL         L12
  3         HALT   (0)   
  4  L10:   LOAD         -1[LB]
  5         LOAD         0[OB]
  6         LOADA        0[OB]
  7         CALLI        L11
  8         RETURN (1)   1
  9  L11:   LOAD         -2[LB]
 10         LOAD         -1[LB]
 11         CALL         add     
 12         LOAD         3[LB]
 13         RETURN (1)   2
 14  L12:   LOADL        -1
 15         LOADL        1
 16         CALL         newobj  
 17         LOAD         3[LB]
 18         LOADL        0
 19         LOADL        3
 20         CALL         fieldupd
 21         LOADL        4
 22         LOAD         3[LB]
 23         CALLI        L10
 24         CALL         putintnl
 25         RETURN (0)   1
