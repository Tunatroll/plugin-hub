/*
 * Copyright (c) 2020 Abex
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.pluginhub.packager;

import com.google.common.io.Files;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import net.runelite.pluginhub.uploader.Util;
import org.junit.Assert;
import org.junit.Test;

@Slf4j
public class PluginTest
{
	private static final String VALID_REPOSITORY = "https://github.com/runelite/example-plugin.git";
	private static final String VALID_COMMIT = "0000000000000000000000000000000000000000";

	@Test
	public void testInternalNameChecks() throws IOException, DisabledPluginException
	{
		try
		{
			new Plugin(new File("plugins/I Like Spaces_and_UNDERSCORES"));
			Assert.fail();
		}
		catch (PluginBuildException e)
		{
			log.info("ok: ", e);
			assertContains(e.getHelpText(), "try: \"i-like-spaces-and-underscores\"");
		}
	}

	@Test
	public void testCommitMustBeComplete() throws DisabledPluginException, IOException
	{
		try
		{
			newPlugin("test", "" +
				"repository=" + VALID_REPOSITORY + "\n" +
				"commit=2357276b");
			Assert.fail();
		}
		catch (PluginBuildException e)
		{
			log.info("ok: ", e);
			assertContains(e.getHelpText(), "commit");
		}
	}

	@Test
	public void testCommitMustBePresent() throws DisabledPluginException, IOException
	{
		PluginBuildException e = assertNewPluginFails("test", "repository=" + VALID_REPOSITORY + "\n");
		assertContains(e.getMessage(), "commit must be a full 40 character sha1sum");
		assertContains(e.getHelpText(), "commit=null");
	}

	@Test
	public void testRepositoryMustBePresent() throws DisabledPluginException, IOException
	{
		PluginBuildException e = assertNewPluginFails("test", "commit=" + VALID_COMMIT + "\n");
		assertContains(e.getMessage(), "repository is missing");
		assertContains(e.getHelpText(), "in file");
	}

	@Test
	public void testRepositoryMustBeGithubCloneUrl() throws DisabledPluginException, IOException
	{
		PluginBuildException ssh = assertNewPluginFails("test", "" +
			"repository=git@github.com:runelite/example-plugin.git\n" +
			"commit=" + VALID_COMMIT + "\n");
		assertContains(ssh.getHelpText(), "repositories must be https clone urls");

		PluginBuildException wrongHost = assertNewPluginFails("test", "" +
			"repository=https://gitlab.com/runelite/example-plugin.git\n" +
			"commit=" + VALID_COMMIT + "\n");
		assertContains(wrongHost.getHelpText(), "repositories must be hosted on GitHub.com");

		PluginBuildException missingGitSuffix = assertNewPluginFails("test", "" +
			"repository=https://github.com/runelite/example-plugin\n" +
			"commit=" + VALID_COMMIT + "\n");
		assertContains(missingGitSuffix.getHelpText(), "repository must be a clone url");
	}

	@Test
	public void testExamplePluginCompiles() throws DisabledPluginException, PluginBuildException, IOException, InterruptedException
	{
		try (Plugin p = createExamplePlugin("example"))
		{
			p.build(Util.readRLVersion(), true);
		}
	}

	@Test
	public void testMissingPlugin() throws DisabledPluginException, PluginBuildException, IOException, InterruptedException
	{
		try (Plugin p = createExamplePlugin("missing-plugin"))
		{
			File propFile = new File(p.repositoryDirectory, "runelite-plugin.properties");
			Properties props = Plugin.loadProperties(propFile);
			props.setProperty("plugins", "com.nonexistent");
			writeProperties(props, propFile);
			p.build(Util.readRLVersion(), true);
			Assert.fail();
		}
		catch (PluginBuildException e)
		{
			log.info("ok: ", e);
			assertContains(e.getHelpText(), "com.example.TestExamplePlugin");
		}
	}

	@Test
	public void testEmptyPlugins() throws DisabledPluginException, PluginBuildException, IOException, InterruptedException
	{
		try (Plugin p = createExamplePlugin("empty-plugins"))
		{
			File propFile = new File(p.repositoryDirectory, "runelite-plugin.properties");
			Properties props = Plugin.loadProperties(propFile);
			props.setProperty("plugins", "");
			writeProperties(props, propFile);
			p.build(Util.readRLVersion(), true);
			Assert.fail();
		}
		catch (PluginBuildException e)
		{
			log.info("ok: ", e);
			assertContains(e.getHelpText(), "com.example.TestExamplePlugin");
		}
	}

	@Test
	public void testUnverifiedDependency() throws InterruptedException, DisabledPluginException, PluginBuildException, IOException
	{
		try (Plugin p = createExamplePlugin("unverified-dependency"))
		{
			File buildFile = new File(p.repositoryDirectory, "build.gradle");
			String buildSrc = Files.asCharSource(buildFile, StandardCharsets.UTF_8).read();
			buildSrc = buildSrc.replace("dependencies {", "dependencies {\n" +
				"	implementation 'org.apache.httpcomponents:httpclient:4.5.13'");
			Files.asCharSink(buildFile, StandardCharsets.UTF_8).write(buildSrc);
			p.build(Util.readRLVersion(), true);
			Assert.fail();
		}
		catch (PluginBuildException e)
		{
			log.info("ok: ", e);
		}
	}

	@Test
	public void testNetRuneLitePackage() throws InterruptedException, DisabledPluginException, PluginBuildException, IOException
	{
		try (Plugin p = createExamplePlugin("net-runelite-package", "net.runelite"))
		{
			p.build(Util.readRLVersion(), true);
			Assert.fail();
		}
		catch (PluginBuildException e)
		{
			assertContains(e.getMessage(), "use of net.runelite package namespace is not allowed");
			log.info("ok: ", e);
		}
	}

	@Test
	public void testNetRuneLitePackageDoesNotBlockExisting() throws InterruptedException, DisabledPluginException, PluginBuildException, IOException
	{
		try (Plugin p = createExamplePlugin("net-runelite-package-preexisting", "net.runelite"))
		{
			p.build(Util.readRLVersion(), false);
		}
	}

	private static void writeProperties(Properties props, File fi) throws IOException
	{
		try (FileOutputStream fos = new FileOutputStream(fi))
		{
			props.store(fos, "");
		}
	}

	private static Plugin newPlugin(String name, String desc) throws DisabledPluginException, PluginBuildException, IOException
	{
		File tmp = Files.createTempDir();
		File f = new File(tmp, name);
		try
		{
			Files.asCharSink(f, StandardCharsets.UTF_8).write(desc);
			return new Plugin(f)
			{
				@Override
				protected void realPluginChecks()
				{
				}
			};
		}
		finally
		{
			f.delete();
			tmp.delete();
		}
	}

	private static Plugin createExamplePlugin(String name) throws DisabledPluginException, PluginBuildException, IOException, InterruptedException
	{
		return createExamplePlugin(name, "com.example");
	}

	private static Plugin createExamplePlugin(String name, String packageName) throws DisabledPluginException, PluginBuildException, IOException, InterruptedException
	{
		Plugin p = newPlugin(name, "" +
			"repository=" + VALID_REPOSITORY + "\n" +
			"commit=" + VALID_COMMIT);

		Assert.assertEquals(0, new ProcessBuilder(
			new File("./create_new_plugin.py").getAbsolutePath(),
			"--noninteractive",
			"--output_directory", p.repositoryDirectory.getAbsolutePath(),
			"--name", "Test Example",
			"--package", packageName,
			"--author", "Test Nobody",
			"--description", "Test An example greeter plugin")
			.inheritIO()
			.start()
			.waitFor());

		return p;
	}

	private static PluginBuildException assertNewPluginFails(String name, String desc) throws DisabledPluginException, IOException
	{
		try (Plugin ignored = newPlugin(name, desc))
		{
			Assert.fail();
			return null;
		}
		catch (PluginBuildException e)
		{
			log.info("ok: ", e);
			return e;
		}
	}

	private static void assertContains(String haystack, String needle)
	{
		Assert.assertTrue(haystack, haystack.contains(needle));
	}
}
