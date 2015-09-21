# Moca

`Moca` is a **web crawler** which is capable of rendering pages with *Javascript*. It is implemented in [Scala](http://scala-lang.org/) and runs on the JVM. By default, pages are rendered using a [WebKit](https://www.webkit.org/) based browser provided by JavaFX. Furthermore, it can be distributed in several machines.

It is very specialized, in the sense that it will only downloaded the content (no processing is done on top of it) and it can associate *ids* to the given seeds.

Politeness can also be configured, making a request to a same partition no more often than, let's say, every 5 seconds.

It is still in a very early state and problems are expected to happen.

## Operation

Each seed is associated to a **Work** unit and URLs to be followed are select by an implementation of the `LinkSelectionCriteria` class. If two distinct **Work** units end up hitting a same website, this website will be downloaded twice for each of the units. When started to run a given **Work**, urls to be downloaded are broken down in **Tasks**.

`Moca` also guarantees that two conflicting tasks are never executed at a same time. Two tasks are considered to be conflicting if they belong to a same partition. A partition is stablished by an implementation of `PartitionSelector`, which by default is the url's host. So urls that share the same host will belong to a same partition.


## Building

```bash
cd $project
./sbt assembly
```

The generated binary will be available on `$project/target/scala-2.11/moca-0.0.1`.


## Running

By default, `Moca` will use *PostgreSQL* for saving state and *AWS S3* for storing downloaded content. The sections below describe how to set up *PostgreSQL* and *FakeS3*, if you wish to run it locally. This behavior is controlled by implementations of `RunControl` and `ContentRepo`, respectively.

A few configuration flags are available when starting it. You can run `moca-0.0.1 --help` for more information. More commonly, if you are running it in several machines, you will want to run something like:

```bash
moca-0.0.1 -S some-host:1731
```

`Moca` uses [Akka](http://akka.io/)'s cluster features in order to interact with all running instances. For that, it is necessary to provide host and port for at least another member of the cluster using the `-S` flag, so the starting node can join the others. More information can be found [here](http://doc.akka.io/docs/akka/snapshot/java/cluster-usage.html#Joining_to_Seed_Nodes).

Besides the flags available, extra configurations can be passed using `-c`. The provided file might look similar to:

```
store.content.s3.endpoint = "http://$host:$port"
store.work.postgres.connection.url = "postgres://$user:$pwd@$host/$dbname"
akka-persistence-sql-async.url = "jdbc:postgresql://$user:$pwd@$host/$dbname"
```

For more details of what can be passed on the config file you can use `main.conf` as reference.

Content is stored in the format given by a `ContentSerializer`. By default *json* is used to store everything in a format like:

```json
{
  "url": "http://www.example.com/",
  "status": 200,
  "headers": {
  	 "Server": ["SimpleHTTP/0.6 Python/2.7.5"],
  	 "Content-Length": ["153"],
  	 "Content-type": ["text/html"]
  },
  "content": "BASE64-ENCODED-CONTENT"
}
```

### Adding seeds

```bash
moca-0.0.1 -p 1732 -S 127.0.0.1:1731 -s seeds.txt
```

### Seeds file example

```
!& default
!= max-depth 3
!= robots-txt
!= same-domain
!= a-href

http://site0.test:8000|1
```

If an *id* is not given for the seed, the *sha1* hash of the url will be used.

#### Criteria

An instance of `LinkSelectionCriteria` determines how a website will be crawled. You can implement it yourself or use some of the existing ones. A [DSL](https://en.wikipedia.org/wiki/Domain-specific_language) is used on the seeds input file to register what is the criteria to be used together with each of the seeds.

Some of the the available implementations are:

* `AHrefCriteria`: selects and resolves all links from the page that appear on *href* attributes of *a* elements;
* `MaxDepthCriteria`: filter out any link that exceeds the defined max depth;
* `RobotsTxtCriteria`: filter out links that are excluded by the website's [robots.txt](http://www.robotstxt.org/);
* `SameDomainCriteria`: filter out links that don't belong to the crawled domain. It uses both the original URL and the rendered URL (if there were any redirects) when deciding;
* `SameHostCriteria`: filter out links that don't belong to the same host, also using the original and rendered URLs for reference;
* `StringJSCriteria`: receives a Javascript script that is expected to return an array of urls, that are then resolved and returned;

When declaring them on the input file, the following format is expected:

```
!& criteria-name
!= n optional filters
!= criteria able to generate links
```

If a criteria has the name `default`, than it will be automatically assigned to any seed that doesn't have another name declared. A specific criteria might be assigned to a seed by adding its name after the *id*, for example:

```
& custom-criteria
!= a-href

http://www.example.com|1|custom-criteria
```

If no `default` is defined, a system default will be used, that is a combination of `AHrefCriteria` with `MaxDepthCriteria(2)`.


### Checking results for a specific seed

```bash
moca-0.0.1 -p 1732 -S 127.0.0.1:1731 -r 1
```

This will return the latest results for a given **Work**, or none if hasn't finished yet.


## Set Up

### PostgreSQL for `PgMapDBWorkRepo`

Just run the following on your console:

```shell
createdb moca
createuser --pwprompt moca
psql -h localhost moca
```

And on psql:

```sql
GRANT ALL PRIVILEGES ON DATABASE "moca" TO "moca";
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA "public" TO "moca";
```


### PostgreSQL for `akka-persistence-sql-async`

Run the following on your console:

```bash
createdb akka-persistence
createuser --pwprompt akka-persistence
psql -h localhost akka-persistence
```

Followed by this on psql:

```sql
CREATE TABLE IF NOT EXISTS journal (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_nr BIGINT NOT NULL,
  message BYTEA NOT NULL,
  PRIMARY KEY (persistence_id, sequence_nr)
);

CREATE TABLE IF NOT EXISTS snapshot (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_nr BIGINT NOT NULL,
  created_at BIGINT NOT NULL,
  snapshot BYTEA NOT NULL,
  PRIMARY KEY (persistence_id, sequence_nr)
);

GRANT ALL PRIVILEGES ON DATABASE "akka-persistence" TO "akka-persistence";
GRANT ALL PRIVILEGES ON TABLE "journal" TO "akka-persistence";
GRANT ALL PRIVILEGES ON TABLE "snapshot" TO "akka-persistence";
```

### Configure PostgreSQL for remote access

Append the following to `postgresql.conf`:

```
listen_addresses = '*'
```

And this to `pg_hba.conf`:

```
host all all 0.0.0.0/0 md5
```


### FakeS3 for `S3ContentRepo`

1. Install rbenv (used [this](https://gorails.com/setup/osx/10.10-yosemite) as reference)
2. Set endpoint to `http://localhost:4568` in `main.conf`
3. Run:

```bash
gem install fakes3
rm -rf s3rver && mkdir s3rver && fakes3 -r s3rver/ -p 4568 -H 192.168.2.105
```


## Testing

Tests can be run using:

```
./sbt test
```

And run the integration tests with:

```
./sbt it:test
```

## Problems

`Moca` is still under development and might present several issues. Here we will document specific behaviors that might bring problems when using it.

### Refetching Content

When a worker starts a task, we will keep working on it till there are no more eligible links to be downloaded. Eligible links are selected by the `LinkSelectionCriteria`. One of the main ways to control downloading is by using a maximum depth, and when starting from a same URL from different depths, the output might be completely different.

Let's assume we have some *workA* which seed is the URL *siteA/a*. It finds a link that takes to another site - url *siteB/a* - where both will at some point converge to the url *siteA/d* but with different depths. You can see a graphical representation of this below:

```
  depth        workA
┌───────┐   ┌─────────┐
│   0   │   │ siteA/a │─ ─ ─ ─ ┐
├───────┤   └─────────┘        ¦
│       │        ¦             ¦
│       │        ▼             ▼
├───────┤   ┌─────────┐   ┌─────────┐
│   1   │   │ siteA/b │   │ siteB/a │─ ─ ─ ─ ┐
├───────┤   └─────────┘   └─────────┘        ¦
│       │        ¦             ¦             ¦
│       │        ▼             ▼             ▼
├───────┤   ┌─────────┐   ┌─────────┐   ╔═════════╗
│   2   │   │ siteA/c │   │   ...   │   ║ siteA/d ║
├───────┤   └─────────┘   └─────────┘   ╚═════════╝
│       │        ¦
│       │        ▼
├───────┤   ╔═════════╗
│   3   │   ║ siteA/d ║
└───────┘   ╚═════════╝

```

On cases where this happens, the crawler will later on start a new task with the url *siteA/d*, but using the lowest existing depth to begin with. This will cause the whole tree bellow that point to be downloaded, but will nevertheless produce the expected output.

Right now, if *siteA/d* contains an url to *siteA/a*, the whole site might be refetched, that is, till the maximum depth. 

A possible improvement would be to only download content if the link depth is smaller or equal to any previously downloaded version of an specific content. Also, this could become a flag where this behavior is allowed or not.


## TODO

* Re-enable Javascript execution on JavaFX's WebKit browser:
  	- it was removed due to issues with the JVM. Getting objects from it seem to make the whole process crash
  	- try using a bridge: <https://blogs.oracle.com/javafx/entry/communicating_between_javascript_and_javafx>
	- Change back BrowserWindow and AHrefCriteria(script) to use respectively:

```javascript 
html = webEngine.executeScript("document.documentElement.outerHTML").toString

Array.prototype.slice.call(document.getElementsByTagName('a')).map(function(e) { return e.href; });
```

* Don't store more than needed for DNS names:
	- Each label may contain up to 63 characters. The full domain name may not exceed the length of 253 characters in its textual representation
	- <https://en.wikipedia.org/wiki/Domain_Name_System>

## References

* <https://wiki.apache.org/nutch/OptimizingCrawls>
