-- !Ups
ALTER TABLE ASSEMBLY_REQ ALTER TX_SPEC TYPE TEXT;
ALTER TABLE ASSEMBLY_REQ ALTER ADDRESS TYPE TEXT;

ALTER TABLE ASSEMBLE_RES ALTER ADDRESS TYPE TEXT;
ALTER TABLE ASSEMBLE_RES ALTER TX_SPEC TYPE TEXT;
ALTER TABLE ASSEMBLE_RES ALTER TX TYPE TEXT;

ALTER TABLE SUMMARY ALTER TX TYPE TEXT;

-- !Downs
ALTER TABLE ASSEMBLY_REQ ALTER TX_SPEC TYPE VARCHAR(10000);
ALTER TABLE ASSEMBLY_REQ ALTER ADDRESS TYPE VARCHAR(2000);

ALTER TABLE ASSEMBLE_RES ALTER ADDRESS TYPE VARCHAR(2000);
ALTER TABLE ASSEMBLE_RES ALTER TX_SPEC TYPE VARCHAR(10000);
ALTER TABLE ASSEMBLE_RES ALTER TX TYPE VARCHAR(10000);

ALTER TABLE SUMMARY ALTER TX TYPE VARCHAR(10000);

