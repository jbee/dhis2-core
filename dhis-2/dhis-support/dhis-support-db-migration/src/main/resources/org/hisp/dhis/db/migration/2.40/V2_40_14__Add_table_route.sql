-- Add route table

DROP TABLE IF EXISTS route;

CREATE TABLE route (
	routeid int8 NOT NULL,
	uid varchar(11) NOT NULL,
	code varchar(50) NULL,
	created timestamp NOT NULL,
	lastupdated timestamp NOT NULL,
	lastupdatedby int8 NULL,
	name varchar(230) NOT NULL,
	description text NULL,
	disabled bool NOT NULL,
	url text NOT NULL,
	headers jsonb NULL DEFAULT '{}'::jsonb,
	auth jsonb NULL DEFAULT '{}'::jsonb,
	authorities jsonb NULL DEFAULT '[]'::jsonb,
	userid int8 NULL,
	translations jsonb  DEFAULT '[]'::jsonb,
	sharing jsonb NULL DEFAULT '{}'::jsonb,
	attributevalues jsonb NULL,
	CONSTRAINT route_pkey PRIMARY KEY (routeid),
	CONSTRAINT route_uid_key UNIQUE (uid),
	CONSTRAINT route_code_key UNIQUE (code),
	CONSTRAINT route_name_key UNIQUE (name),
	CONSTRAINT fk_route_lastupdateby_userinfoid FOREIGN KEY (lastupdatedby) REFERENCES userinfo(userinfoid),
	CONSTRAINT fk_route_userid_userinfoid FOREIGN KEY (userid) REFERENCES userinfo(userinfoid)
);
