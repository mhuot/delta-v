/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.opennms.smoketest;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.opennms.smoketest.ui.framework.search.CentralSearch;
import org.opennms.smoketest.ui.framework.search.result.ContextSearchResult;
import org.opennms.smoketest.ui.framework.search.result.SearchContext;
import org.opennms.smoketest.ui.framework.search.result.SearchResult;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MenuHeaderIT extends OpenNMSSeleniumIT {
    private static final Logger LOG = LoggerFactory.getLogger(MenuHeaderIT.class);

    @Test
    public void testMenuEntries() throws Exception {
        // See opennms-webapp/src/main/webapp/WEB-INF/menu-template.json for the menu template
        LOG.debug("testMenuEntries");

        frontPage();

        WebElement foundElement = null;

        // Dashboards Menu
        clickMenuItem("dashboardsMenu", "Wallboard");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[starts-with(@id, 'opennmsvaadinwallboard-')]")));

        frontPage();
        clickMenuItem("dashboardsMenu", "Heatmap");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//div[@class='card-header']/a[starts-with(text(), 'Alarm Heatmap')]")));

        clickMenuItem("dashboardsMenu", "Trends");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//div[@class='card-header']/span[text()='Trend']")));

        clickMenuItem("dashboardsMenu", "Charts");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("include-charts")));

        clickMenuItem("dashboardsMenu", "Database Reports");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//a[@data-name='report-templates']")));

        clickMenuItem("dashboardsMenu", "Metrics Statistics (statsd)");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@class='card-header']/span")));

        clickMenuItem("dashboardsMenu", "Metrics Dashboard (KSC Reports)");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@class='card-header']/span[text()='Customized Reports']")));

        clickMenuItem("dashboardsMenu", "Surveillance Dashboard");
        driver.switchTo().frame(findElementByXpath("/html/body/div/iframe"));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//span[text()='Surveillance view: default']")));

        driver.switchTo().parentFrame();
        frontPage();

        final String reportsMenuName = "name=nav-Reports-top";
        clickMenuItem(reportsMenuName, "Resource Graphs", "graph/index.jsp");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//label[contains(text()[normalize-space()], 'Standard Resource')]")));

        final String dashboardsMenuName = "name=nav-Dashboards-top";
        clickMenuItem(dashboardsMenuName, "Dashboard", "dashboard.jsp");
        // switchTo() by xpath is much faster than by ID
        driver.switchTo().frame(findElementByXpath("/html/body/div/iframe"));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//span[text()='Surveillance view: default']")));
        driver.switchTo().parentFrame();
        frontPage();

        // Metrics Menu
        clickMenuItem("metricsMenu", "Resource Graphs");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[@class='router-link-active router-link-exact-active'][contains(text()[normalize-space()], 'Resource Graphs')]")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//li[@class='feather-list-header'][contains(text()[normalize-space()], 'Resources')]")));

        // Distributed Monitoring
        clickMenuItem("distributedMonitoringMenu", "Manage Minions");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//ol[@class='breadcrumb']/li[contains(text()[normalize-space()], 'Manage Minions')]")));

        clickMenuItem("distributedMonitoringMenu", "Manage Applications");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//ol[@class='breadcrumb']/li[contains(text()[normalize-space()], 'Applications')]")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@class='card-header']/span[text()='Applications']")));

        clickMenuItem("distributedMonitoringMenu", "Manage Monitoring Locations");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//ol[@class='breadcrumb']/li[contains(text()[normalize-space()], 'Monitoring Locations')]")));

        // Manage Inventory Menu
        clickMenuItem("manageInventoryMenu", "Provisioning Requisitions");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//ol[@class='breadcrumb']/li[contains(text()[normalize-space()], 'Provisioning Requisitions')]")));

        clickMenuItem("manageInventoryMenu", "Scheduled Node Discovery");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//ol[@class='breadcrumb']/li[contains(text()[normalize-space()], 'Modify Configuration')]")));

        clickMenuItem("manageInventoryMenu", "One-shot Node Discovery");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//ol[@class='breadcrumb']/li[contains(text()[normalize-space()], 'Create Discovery Scan')]")));

        // quick-add node screen
        clickMenuItem("manageInventoryMenu", "Add a Single Node");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//ol[@class='breadcrumb']/li[contains(text()[normalize-space()], 'Quick-Add Node')]")));

        clickMenuItem("manageInventoryMenu", "Delete Nodes");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@class='card-header']/span[text()='Delete Nodes']")));

        clickMenuItem("manageInventoryMenu", "Manage Business Services");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//ol[@class='breadcrumb']/li[contains(text()[normalize-space()], 'Business Services')]")));
        frontPage();
        clickMenuItemWithIcon("nav-Info-top", "External Requisitions", "opennms/ui/index.html#/configuration");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("app")));
        findElementByXpath("//div[@id='app']//h1[text()='Configuration']");

        Unreliables.retryUntilSuccess(60, TimeUnit.SECONDS, () -> {
            frontPage();
            Thread.sleep(200);

            logout();

            findElementById("input_j_username");
            return null;
        });
    }

    @Test
    public void testLogout() {
        Unreliables.retryUntilSuccess(60, TimeUnit.SECONDS, () -> {
            frontPage();
            Thread.sleep(200);

            logout();

            findElementById("input_j_username");
            return null;
        });
    }

    @Test
    public void verifyCentralSearch() {
        LOG.debug("In verifyCentralSearch");

        frontPage();

        // get the central search text input control and search for "Configure"
        WebElement searchInput = findElementByXpath("//div[@id='onms-central-search-control']/div[@class='onms-search-input-wrapper']/input[@class='search-input']");
        searchInput.sendKeys("Configure");

        // Get the search result context header
        List<WebElement> searchHeaderResults = findElementsByXpath("//div[@id='onms-central-search-control']/div[@class='search-results-dropdown']/div[@class='search-category']");
        assertThat(searchHeaderResults.size(), is(1));

        List<WebElement> searchResultsHeaderElem = findElementsByXpath("//div[@id='onms-central-search-control']/div[@class='search-results-dropdown']/div[@class='search-category']/div[@class='onms-search-result-header']");
        assertThat(searchResultsHeaderElem .size(), is(1));
        assertThat(searchResultsHeaderElem .get(0).getText(), is("Action"));

        // Get the search result items
        List<WebElement> searchResultItems = findElementsByXpath("//div[@id='onms-central-search-control']/div[@class='search-results-dropdown']/div[@class='search-result-item']");

        // 10 actual results
        assertThat(searchResultItems.size(), is(10));

        // TODO:
        // - load more search results
        // - possibly remove CentralSearch code (ui/framework/search) if no longer being used

        frontPage();

        // TODO: Will probably remove this code
        if (false) {
            // Kick off search
            final SearchResult searchResult = new CentralSearch(getDriver()).search("Configure");
            assertThat(searchResult.size(), is(10L));

            // TODO!
            // Load more elements
            final ContextSearchResult contextSearchResult = searchResult.forContext(SearchContext.Action);
            assertThat(contextSearchResult.hasMore(), is(true));
            contextSearchResult.loadMore();
            assertThat(contextSearchResult.size(), is(14L));

            // Select last element from the now loaded elements
            contextSearchResult.getItem("Configure Users").click();
            getDriver().getCurrentUrl().endsWith("/opennms/admin/userGroupView/users/list.jsp");

            // Go back to start page
            new CentralSearch(getDriver()).search("Home").getSingleItem().click();
            getDriver().getCurrentUrl().endsWith("/opennms/index.jsp");
        }
    }

    // We need this helper method, as with the icon used in some menus the contains(text(),...) method does not work anymore
    private void clickMenuItemWithIcon(String menuEntryName, String submenuText, String submenuHref) {
        LoggerFactory.getLogger(getClass()).debug("clickMenuItemWithIcon: menuEntryName={}, submenuText={}, submenuHref={}", menuEntryName, submenuText, submenuHref);

        // Repeat the process altering the offset slightly each time
        final AtomicInteger offset = new AtomicInteger(10);
        final WebDriverWait shortWait = new WebDriverWait(getDriver(), Duration.ofSeconds(1));

        try {
            setImplicitWait(5, TimeUnit.SECONDS);
            Unreliables.retryUntilSuccess(30, TimeUnit.SECONDS, () -> {
                final Actions action = new Actions(getDriver());
                final WebElement headerElement = findElementByXpath(String.format("//li/a[@name='%s']", menuEntryName));

                // Move to element to make sub menu visible
                action.moveToElement(headerElement, offset.get(), offset.get()).perform();
                if (offset.incrementAndGet() > 10) {
                    offset.set(0);
                }

                // Wait until sub menu element is visible, then click
                final WebElement submenuElement = headerElement.findElement(By.xpath(String.format("./../div/a[contains(@class, 'dropdown-item') and contains(@href, '%s')]", submenuHref)));
                shortWait.until(ExpectedConditions.visibilityOf(submenuElement));
                assertEquals(submenuText, submenuElement.getText().trim());
                submenuElement.click();
                return null;
            });
        } finally {
            setImplicitWait();
        }
    }
}
