--
--  Copyright 2005-2018 Red Hat, Inc.
--
--  Red Hat licenses this file to you under the Apache License, version
--  2.0 (the "License"); you may not use this file except in compliance
--  with the License.  You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
--  Unless required by applicable law or agreed to in writing, software
--  distributed under the License is distributed on an "AS IS" BASIS,
--  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
--  implied.  See the License for the specific language governing
--  permissions and limitations under the License.
--

drop schema if exists report cascade;

create schema report;

create table report.incident (
  id serial not null primary key,
  date timestamp,
  name varchar(35),
  summary varchar(35),
  details varchar(255),
  email varchar(60)
);

insert into report.incident (date, name, summary, details, email)
values ('2018-02-20 08:00:00', 'User 1', 'Incident 1', 'This is a report incident 001', 'user1@redhat.com');
insert into report.incident (date, name, summary, details, email)
values ('2018-02-20 08:10:00', 'User 2', 'Incident 2', 'This is a report incident 002', 'user2@redhat.com');
insert into report.incident (date, name, summary, details, email)
values ('2018-02-20 08:20:00', 'User 3', 'Incident 3', 'This is a report incident 003', 'user3@redhat.com');
insert into report.incident (date, name, summary, details, email)
values ('2018-02-20 08:30:00', 'User 4', 'Incident 4', 'This is a report incident 004', 'user4@redhat.com');
