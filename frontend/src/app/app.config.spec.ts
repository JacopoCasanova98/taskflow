import { TestBed } from '@angular/core/testing';
import { appConfig } from './app.config';
import { API_BASE_URL } from './core/config/api-base-url';

describe('Application configuration', () => {
  it('provides the same-origin API base path to application consumers', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    expect(TestBed.inject(API_BASE_URL)).toBe('/api');
  });
});
