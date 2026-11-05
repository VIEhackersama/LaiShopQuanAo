import { test, expect } from '@playwright/test';

test.describe('Image Search Frontend Flow', () => {
  test('uploads image, navigates to /search?queryId=..., displays results, and refines filters', async ({ page }) => {
    const mockImageResults = {
      queryId: 888,
      type: 'IMAGE',
      status: 'COMPLETED',
      text: null,
      conditions: { categoryId: null, sizes: [], colors: [], minPrice: null, maxPrice: null, sort: 'RELEVANCE' },
      products: {
        items: [
          {
            id: 101,
            name: 'Áo Thun Trơn Basic',
            description: 'Chất cotton thoáng mát',
            imageUrl: '/catalog/photos/ao-thun.jpg',
            minPrice: '199000',
            maxPrice: '199000',
            colors: ['Kem', 'Trắng'],
            inStock: true
          },
          {
            id: 102,
            name: 'Áo Polo Thể Thao',
            description: 'Phong cách năng động',
            imageUrl: '/catalog/photos/ao-polo.jpg',
            minPrice: '259000',
            maxPrice: '259000',
            colors: ['Đen'],
            inStock: true
          }
        ],
        page: 0,
        pageSize: 24,
        total: 2
      }
    };

    const mockRefinedResults = {
      queryId: 889,
      type: 'IMAGE',
      status: 'COMPLETED',
      text: null,
      conditions: { categoryId: null, sizes: ['M'], colors: [], minPrice: null, maxPrice: null, sort: 'RELEVANCE' },
      products: {
        items: [
          {
            id: 101,
            name: 'Áo Thun Trơn Basic',
            description: 'Chất cotton thoáng mát',
            imageUrl: '/catalog/photos/ao-thun.jpg',
            minPrice: '199000',
            maxPrice: '199000',
            colors: ['Kem'],
            inStock: true
          }
        ],
        page: 0,
        pageSize: 24,
        total: 1
      }
    };

    // Mock capabilities
    await page.route('**/api/v1/search/capabilities', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ voice: true, image: true })
      });
    });

    // Mock image search endpoint
    await page.route('**/api/v1/search/image', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockImageResults)
      });
    });

    // Mock query result fetch
    await page.route('**/api/v1/search/queries/888*', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockImageResults)
      });
    });

    // Mock query refinement
    await page.route('**/api/v1/search/queries/888/refine', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockRefinedResults)
      });
    });

    // 1. Visit homepage
    await page.goto('/');

    // 2. Open Image Search Dialog
    const imageBtn = page.getByTestId('image-open');
    await expect(imageBtn).toBeVisible();
    await imageBtn.click();

    // 3. Verify dialog is open and image search is enabled
    await expect(page.getByRole('heading', { name: 'Tìm từ một hình ảnh.' })).toBeVisible();
    const fileInput = page.getByTestId('image-file');
    await expect(fileInput).toBeEnabled();

    // 4. Select a dummy image file (1x1 PNG)
    const dummyPng = Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
      'base64'
    );
    await fileInput.setInputFiles({
      name: 'test-search.png',
      mimeType: 'image/png',
      buffer: dummyPng
    });

    // 5. Submit search
    const submitBtn = page.getByTestId('media-submit');
    await expect(submitBtn).toBeEnabled();
    await expect(submitBtn).toHaveText('Tìm sản phẩm tương tự');
    await submitBtn.click();

    // 6. Verify navigation to /search?queryId=888
    await expect(page).toHaveURL(/\/search\?queryId=888$/);

    // 7. Verify search results are displayed
    await expect(page.getByText('2 sản phẩm')).toBeVisible();
    await expect(page.getByTestId('product-101')).toBeVisible();
    await expect(page.getByTestId('product-102')).toBeVisible();
    await expect(page.getByText('Áo Thun Trơn Basic')).toBeVisible();
    await expect(page.getByText('Áo Polo Thể Thao')).toBeVisible();

    // 8. Test refinement: filter by Size M
    const filterOpenBtn = page.getByTestId('filter-open');
    if (await filterOpenBtn.isVisible()) {
      await filterOpenBtn.click();
    }
    const sizeMBtn = page.getByTestId('filter-size-M');
    await sizeMBtn.click();

    // URL should have sizes=M while keeping queryId=888
    await expect(page).toHaveURL(/queryId=888.*sizes=M/);
    await expect(page.getByText('1 sản phẩm')).toBeVisible();
    await expect(page.getByTestId('product-101')).toBeVisible();
    await expect(page.getByTestId('product-102')).not.toBeVisible();

    // 9. Reset filter: click "Xóa bộ lọc"
    await page.getByRole('button', { name: 'Xóa bộ lọc' }).click();
    await expect(page).toHaveURL(/\/search\?queryId=888$/);
    await expect(page.getByText('2 sản phẩm')).toBeVisible();
    await expect(page.getByTestId('product-101')).toBeVisible();
    await expect(page.getByTestId('product-102')).toBeVisible();
  });
});
