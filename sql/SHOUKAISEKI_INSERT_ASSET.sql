--------------------------------------------------------
--  DDL for Table SHOUKAISEKI_INSERT_ASSET
--------------------------------------------------------

  CREATE TABLE "SHOUKAISEKI_INSERT_ASSET" 
   (	"ASSET" VARCHAR2(200 BYTE), 
	"COLUMN2" VARCHAR2(200 BYTE), 
	"DESCRIPTION" VARCHAR2(200 BYTE), 
	"COLUMN4" VARCHAR2(200 BYTE), 
	"COLUMN5" VARCHAR2(200 BYTE), 
	"SITEID" VARCHAR2(200 BYTE), 
	"COLUMN8" VARCHAR2(200 BYTE), 
	"COLUMN7" VARCHAR2(200 BYTE), 
	"SN" NUMBER, 
	"ASSETNUMBAK" VARCHAR2(200 BYTE)
   ) SEGMENT CREATION IMMEDIATE 
  PCTFREE 10 PCTUSED 40 INITRANS 1 MAXTRANS 255 
 NOCOMPRESS LOGGING
  STORAGE(INITIAL 65536 NEXT 1048576 MINEXTENTS 1 MAXEXTENTS 2147483645
  PCTINCREASE 0 FREELISTS 1 FREELIST GROUPS 1
  BUFFER_POOL DEFAULT FLASH_CACHE DEFAULT CELL_FLASH_CACHE DEFAULT)
  TABLESPACE "MAXDATA" ;
